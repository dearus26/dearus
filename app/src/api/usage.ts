import { request } from '@/api/client';
import type { components } from '@/api/schema';

export type Usage = components['schemas']['Usage'];

/** GET /me/usage/today — 오늘 대화한 시간(분)과 권장 시간. 대화 화면 위의 "오늘 대화 12분 · 하루 30분 정도를 권장해요"에 써요. */
export function getTodayUsage() {
  return request<Usage>('GET', '/me/usage/today');
}
