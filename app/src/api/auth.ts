import { request } from '@/api/client';
import type { components } from '@/api/schema';

export type User = components['schemas']['User'];
export type AuthResponse = components['schemas']['AuthResponse'];
export type ConsentInput = components['schemas']['UserConsentRequest'];
export type Provider = 'kakao' | 'google' | 'apple' | 'dev';

/** POST /auth/{provider} — 소셜 로그인. 처음이면 계정이 만들어져요. */
export function login(provider: Provider, token: string, nickname?: string) {
  return request<AuthResponse>('POST', `/auth/${provider}`, { body: { token, nickname }, auth: false });
}

/** GET /me — 내 정보 */
export function getMe() {
  return request<User>('GET', '/me');
}

/** PUT /me/consent — 서비스 이용 동의(필수 3개 + 알림 선택) */
export function giveConsent(input: ConsentInput) {
  return request<User>('PUT', '/me/consent', { body: input });
}

/** DELETE /me/consent — AI 분석 동의 철회 */
export function revokeAiConsent() {
  return request<User>('DELETE', '/me/consent');
}

/** DELETE /me — 회원 탈퇴 */
export function deleteMe() {
  return request<void>('DELETE', '/me');
}
