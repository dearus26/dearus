import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  acceptInvite,
  createInvite,
  getCouple,
  getCoupleComparison,
  giveCoupleConsent,
  listCoupleDays,
  revokeCouple,
  type CoupleScope,
  type CoupleState,
} from '@/api/couples';
import { chatKeys } from '@/features/past/hooks';

export const coupleKeys = {
  all: ['couple'] as const,
  state: ['couple', 'state'] as const,
  days: (from: string, to: string) => ['couple', 'days', from, to] as const,
  comparison: (anchorDate: string, scope: CoupleScope) => ['couple', 'comparison', anchorDate, scope] as const,
};

/** 상대를 기다리는 동안 상태를 다시 확인하는 간격(명세: 3~5초) */
const WAITING_POLL_MS = 4000;

/**
 * 내 커플 상태. 코드를 만들고 상대를 기다리는 중이거나, 연결됐지만 상대가 아직 동의하지 않았으면 4초마다 다시 확인해요.
 * enabled가 false이면(개인 모드 대화 등) 부르지 않아요.
 */
export function useCouple(enabled = true) {
  return useQuery({
    queryKey: coupleKeys.state,
    queryFn: enabled ? getCouple : skipToken,
    staleTime: 0,
    refetchInterval: (query) => {
      const state = query.state.data;
      if (!state) return false;
      if (state.status === 'PENDING') return WAITING_POLL_MS;
      if (state.status === 'CONNECTED' && !state.compareOpen && state.me?.consented) return WAITING_POLL_MS;
      return false;
    },
  });
}

/** 함께 볼 수 있는 날(두 사람의 기록이 모두 있는 날, 일기 또는 그날 올린 메신저). 비교가 열리지 않았으면(enabled false) 부르지 않아요. */
export function useCoupleDays(from: string, to: string, enabled: boolean) {
  return useQuery({
    queryKey: coupleKeys.days(from, to),
    queryFn: enabled ? async () => (await listCoupleDays(from, to)).items : skipToken,
  });
}

/**
 * 같은 날 두 시점 비교. 상대가 끝내기를 기다리거나(WAITING_PARTNER) 요약 · 간극을 만드는 중(GENERATING)이면 4초마다 다시 확인해요.
 * 대화를 하고 돌아오면 바뀌어 있을 수 있어서 열 때마다 새로 받아요.
 */
export function useCoupleComparison(anchorDate: string, scope: CoupleScope) {
  return useQuery({
    queryKey: coupleKeys.comparison(anchorDate, scope),
    queryFn: () => getCoupleComparison(anchorDate, scope),
    staleTime: 0,
    refetchOnMount: 'always',
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === 'WAITING_PARTNER' || status === 'GENERATING' ? WAITING_POLL_MS : false;
    },
  });
}

/** 커플 상태를 바꾸는 요청은 모두 새 상태를 돌려줘서, 받은 상태를 그대로 저장해요. */
function useStateMutation<Input = void>(mutationFn: (input: Input) => Promise<CoupleState>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: (state) => {
      queryClient.setQueryData(coupleKeys.state, state);
    },
  });
}

export function useCreateInvite() {
  return useStateMutation(() => createInvite());
}

export function useAcceptInvite() {
  return useStateMutation((code: string) => acceptInvite(code));
}

export function useGiveCoupleConsent() {
  return useStateMutation(() => giveCoupleConsent());
}

/** 철회 · 연결 해제. 이 커플의 대화와 리포트가 서버에서 지워지니, 앱에 남은 함께 볼 날 · 비교 · 대화 기록도 비워요. */
export function useRevokeCouple() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => revokeCouple(),
    onSuccess: (state) => {
      queryClient.setQueryData(coupleKeys.state, state);
      queryClient.removeQueries({ queryKey: [...coupleKeys.all, 'days'] });
      queryClient.removeQueries({ queryKey: [...coupleKeys.all, 'comparison'] });
      // 지워진 커플 대화를 화면이 다시 부르지 않게, 저장해 둔 대화 내용도 함께 버려요
      queryClient.removeQueries({ queryKey: [...chatKeys.all, 'messages'] });
    },
  });
}
