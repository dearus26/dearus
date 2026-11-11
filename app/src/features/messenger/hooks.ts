import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  deleteMessengerUpload,
  getMessengerUpload,
  listMessengerUploads,
  uploadMessengerTxt,
} from '@/api/uploads';
import type { PendingUpload } from '@/features/messenger/pending';
import { personaKeys } from '@/features/past/hooks';

export const uploadKeys = {
  all: ['uploads'] as const,
  list: ['uploads', 'list'] as const,
  detail: (id: string) => ['uploads', 'detail', id] as const,
};

/** 설정 "내 데이터"의 올린 메신저 대화 목록(최신순). 분석 중인 게 있으면 끝날 때까지 2초마다 다시 받아요. */
export function useMessengerUploads() {
  return useQuery({
    queryKey: uploadKeys.list,
    queryFn: async () => (await listMessengerUploads()).items,
    staleTime: 0,
    refetchInterval: (query) => (query.state.data?.some((upload) => upload.status === 'PROCESSING') ? 2000 : false),
  });
}

/** 올린 대화를 지워요. 내 말 원문과 이 업로드로 만든 주간 요약이 함께 지워지고, 시점 고르기의 메신저 기록량도 달라져요. */
export function useDeleteUpload() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteMessengerUpload(id),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: uploadKeys.all }),
        queryClient.invalidateQueries({ queryKey: personaKeys.all }),
      ]),
  });
}

/** 올린 대화의 분석 상태. 분석 중(PROCESSING)이면 1.5초마다 다시 확인해요. */
export function useMessengerUpload(id: string) {
  return useQuery({
    queryKey: uploadKeys.detail(id),
    queryFn: () => getMessengerUpload(id),
    refetchInterval: (query) => (query.state.data?.status === 'PROCESSING' ? 1500 : false),
  });
}

/** txt에서 추린 내 말을 올려요. 받은 상태를 바로 채워 두면 상태 화면이 같은 요청을 또 보내지 않아요. */
export function useStartUpload() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (pending: PendingUpload) => uploadMessengerTxt(pending.messages),
    onSuccess: (upload) => {
      queryClient.setQueryData(uploadKeys.detail(upload.id), upload);
      void queryClient.invalidateQueries({ queryKey: uploadKeys.list });
    },
  });
}
