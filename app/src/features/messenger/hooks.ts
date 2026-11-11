import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { getMessengerUpload, uploadMessengerImages, uploadMessengerTxt } from '@/api/uploads';
import type { PendingUpload } from '@/features/messenger/pending';

export const uploadKeys = {
  all: ['uploads'] as const,
  detail: (id: string) => ['uploads', 'detail', id] as const,
};

/** 올린 대화의 분석 상태. 분석 중(PROCESSING)이면 1.5초마다 다시 확인해요. */
export function useMessengerUpload(id: string) {
  return useQuery({
    queryKey: uploadKeys.detail(id),
    queryFn: () => getMessengerUpload(id),
    refetchInterval: (query) => (query.state.data?.status === 'PROCESSING' ? 1500 : false),
  });
}

/** txt에서 추린 내 말, 또는 스크린샷을 올려요. 받은 상태를 바로 채워 두면 상태 화면이 같은 요청을 또 보내지 않아요. */
export function useStartUpload() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (pending: PendingUpload) =>
      pending.kind === 'TXT' ? uploadMessengerTxt(pending.messages) : uploadMessengerImages(pending.images),
    onSuccess: (upload) => {
      queryClient.setQueryData(uploadKeys.detail(upload.id), upload);
    },
  });
}
