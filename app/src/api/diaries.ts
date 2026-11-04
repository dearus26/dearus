import { request } from '@/api/client';
import type { components } from '@/api/schema';

export type Diary = components['schemas']['Diary'];
export type DiaryCreateInput = components['schemas']['DiaryCreate'];
export type DiaryUpdateInput = components['schemas']['DiaryUpdate'];

/** GET /diaries — 기간(최대 93일)의 일기. 홈 캘린더는 한 달치를 받아요. */
export function listDiaries(from: string, to: string) {
  return request<components['schemas']['DiaryList']>('GET', '/diaries', { query: { from, to } });
}

/** POST /diaries — 하루에 한 건. 같은 날짜가 있으면 409 DIARY_ALREADY_EXISTS(details.diaryId) */
export function createDiary(input: DiaryCreateInput) {
  return request<Diary>('POST', '/diaries', { body: input });
}

/** PUT /diaries/{id} — 날짜는 못 바꾸고 본문과 감정 태그만 통째로 바꿔요. */
export function updateDiary(id: string, input: DiaryUpdateInput) {
  return request<Diary>('PUT', `/diaries/${id}`, { body: input });
}

/** DELETE /diaries/{id} */
export function deleteDiary(id: string) {
  return request<void>('DELETE', `/diaries/${id}`);
}
