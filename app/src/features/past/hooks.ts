import { skipToken, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { createChat, getChat, listChats, type ChatCreateInput } from '@/api/chats';
import { getShortcuts, previewPersona, type PersonaScope } from '@/api/personas';

export const personaKeys = {
  all: ['personas'] as const,
  shortcuts: ['personas', 'shortcuts'] as const,
  preview: (anchor: string, scope: PersonaScope) => ['personas', 'preview', anchor, scope] as const,
};

export const chatKeys = {
  all: ['chats'] as const,
  list: ['chats', 'list'] as const,
  detail: (id: string) => ['chats', 'detail', id] as const,
};

/** "과거의 나" 목록. 시점을 분석 중인 항목이 있으면 끝날 때까지 2초마다 다시 받아서 "불러오는 중"이 남지 않게 해요. */
export function useChats() {
  return useQuery({
    queryKey: chatKeys.list,
    queryFn: async () => (await listChats()).items,
    refetchInterval: (query) => (query.state.data?.some((chat) => chat.status === 'PREPARING') ? 2000 : false),
  });
}

/** 1개월 · 6개월 · 1년 전 바로가기. 기록량(일기 건수 등)이 함께 와요. */
export function useShortcuts() {
  return useQuery({
    queryKey: personaKeys.shortcuts,
    queryFn: async () => (await getShortcuts()).items,
  });
}

/** 캘린더에서 고른 날의 기록량. 날짜를 고르기 전(null)에는 부르지 않아요. */
export function usePersonaPreview(anchor: string | null, scope: PersonaScope) {
  return useQuery({
    queryKey: personaKeys.preview(anchor ?? '', scope),
    queryFn: anchor === null ? skipToken : () => previewPersona(anchor, scope),
  });
}

/** 대화 세션 상태. 시점을 분석하는 중(PREPARING)이면 1.5초마다 다시 확인해요. */
export function useChat(id: string) {
  return useQuery({
    queryKey: chatKeys.detail(id),
    queryFn: () => getChat(id),
    refetchInterval: (query) => (query.state.data?.status === 'PREPARING' ? 1500 : false),
  });
}

export function useCreateChat() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: ChatCreateInput) => createChat(input),
    onSuccess: (session) => {
      // 대화 화면이 열리자마자 같은 요청을 또 보내지 않게, 받은 세션을 바로 채워 둬요
      queryClient.setQueryData(chatKeys.detail(session.id), session);
      return queryClient.invalidateQueries({ queryKey: chatKeys.list });
    },
  });
}
