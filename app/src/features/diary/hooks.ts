import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { createDiary, deleteDiary, listDiaries, updateDiary, type Diary } from '@/api/diaries';
import { personaKeys } from '@/features/past/hooks';
import { monthRange } from '@/lib/date';
import type { Emotion } from '@/theme/colors';

export const diaryKeys = {
  all: ['diaries'] as const,
  month: (year: number, month: number) => ['diaries', 'month', year, month] as const,
};

/** 한 달치 일기. 홈 캘린더의 점과 날짜 시트, 일기 쓰기 화면(이미 쓴 날인지 확인)이 함께 써요. */
export function useMonthDiaries(year: number, month: number) {
  return useQuery({
    queryKey: diaryKeys.month(year, month),
    queryFn: async (): Promise<Diary[]> => {
      const { from, to } = monthRange(year, month);
      return (await listDiaries(from, to)).items;
    },
  });
}

type SaveInput = {
  /** 있으면 고치기(PUT), 없으면 새로 쓰기(POST) */
  id?: string;
  date: string;
  content: string;
  emotions: Emotion[];
};

/** 일기가 바뀌면 캘린더와, "과거의 나" 시점 고르기에 보이는 기록량(일기 건수 · 감정)도 다시 받아요. */
function refreshAfterDiaryChange(queryClient: ReturnType<typeof useQueryClient>) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: diaryKeys.all }),
    queryClient.invalidateQueries({ queryKey: personaKeys.all }),
  ]);
}

export function useSaveDiary() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, date, content, emotions }: SaveInput) =>
      id ? updateDiary(id, { content, emotions }) : createDiary({ date, content, emotions }),
    onSuccess: () => refreshAfterDiaryChange(queryClient),
  });
}

export function useDeleteDiary() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteDiary(id),
    onSuccess: () => refreshAfterDiaryChange(queryClient),
  });
}
