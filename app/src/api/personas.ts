import { request } from '@/api/client';
import type { components } from '@/api/schema';

export type PersonaWindow = components['schemas']['PersonaWindow'];
export type PersonaScope = components['schemas']['PersonaScope'];
export type Shortcut = components['schemas']['Shortcut'];
export type ShortcutKey = components['schemas']['ShortcutKey'];
export type RecordLevel = components['schemas']['RecordLevel'];

/** GET /personas/shortcuts — 1개월 · 6개월 · 1년 전 바로가기(전후 2주). 기준일은 서버가 그 주 월요일로 맞춰 줘요. */
export function getShortcuts() {
  return request<components['schemas']['ShortcutList']>('GET', '/personas/shortcuts');
}

/** GET /personas/preview — 캘린더에서 고른 날(DAY)의 기록량. AI를 부르지 않는 가벼운 조회예요. */
export function previewPersona(anchor: string, scope: PersonaScope) {
  return request<PersonaWindow>('GET', '/personas/preview', { query: { anchor, scope } });
}
