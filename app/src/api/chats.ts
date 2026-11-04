import { request } from '@/api/client';
import type { components } from '@/api/schema';

export type ChatListItem = components['schemas']['ChatListItem'];
export type ChatSession = components['schemas']['ChatSession'];
export type ChatCreateInput = components['schemas']['ChatCreate'];

/** GET /chats — 과거의 나 목록(최근 대화 순, 최대 50개) */
export function listChats() {
  return request<components['schemas']['ChatList']>('GET', '/chats');
}

/**
 * POST /chats — 고른 시점의 대화 세션을 만들어요. 분석이 필요하면 PREPARING으로 돌려줘서 GET /chats/{id}로 기다려요.
 * 403 AI_CONSENT_REQUIRED(AI 분석 동의 없음), 400 ANCHOR_OUT_OF_RANGE(고를 수 없는 날짜)
 */
export function createChat(input: ChatCreateInput) {
  return request<ChatSession>('POST', '/chats', { body: input });
}

/** GET /chats/{id} — 준비 중인지, 정확도가 어떤지, 끝났는지 */
export function getChat(id: string) {
  return request<ChatSession>('GET', `/chats/${encodeURIComponent(id)}`);
}
