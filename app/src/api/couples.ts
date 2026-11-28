import { request } from '@/api/client';
import type { components } from '@/api/schema';

export type CoupleState = components['schemas']['CoupleState'];
export type CoupleDay = components['schemas']['CoupleDay'];
export type CoupleComparison = components['schemas']['CoupleComparison'];
export type CoupleScope = components['schemas']['PersonaScope'];

/** GET /couples/me — 내 커플 상태. 연결이 없으면 status NONE이에요. 기다리는 화면에서는 3~5초마다 다시 불러요. */
export function getCouple() {
  return request<CoupleState>('GET', '/couples/me');
}

/**
 * POST /couples/invite — "초대 코드 만들기"를 누른 시점에 코드를 만들어요(24시간 유효). 아직 유효한 코드가 있으면 같은 코드예요.
 * 409 ALREADY_IN_COUPLE(이미 연결됨)
 */
export function createInvite() {
  return request<CoupleState>('POST', '/couples/invite');
}

/**
 * POST /couples/invite/accept — 받은 코드를 입력해 연결해요(연결만 되고, 둘 다 동의해야 대화할 수 있어요).
 * 400 COUPLE_OWN_CODE(내가 만든 코드) · 404 COUPLE_CODE_INVALID · 409 ALREADY_IN_COUPLE · 410 COUPLE_CODE_EXPIRED
 */
export function acceptInvite(inviteCode: string) {
  return request<CoupleState>('POST', '/couples/invite/accept', { body: { inviteCode } });
}

/** POST /couples/consent — 내 동의를 기록해요. 둘 다 동의하면 compareOpen이 true예요. 409 COUPLE_NOT_CONNECTED */
export function giveCoupleConsent() {
  return request<CoupleState>('POST', '/couples/consent', { body: { agreed: true } });
}

/**
 * GET /couples/days — 두 사람의 기록(일기 또는 그날 올린 메신저)이 모두 있는 날(오늘부터 1년 전까지, 한 번에 최대 93일). 감정이 엇갈린 날은 recommended예요.
 * 403 COUPLE_ACCESS_DENIED(연결 · 양측 동의가 아니거나 철회함)
 */
export function listCoupleDays(from: string, to: string) {
  return request<components['schemas']['CoupleDayList']>('GET', '/couples/days', { query: { from, to } });
}

/** GET /couples/comparisons — 같은 날 두 사람이 각자 그날의 연인과 나눈 대화의 요약과 간극. 403 COUPLE_ACCESS_DENIED */
export function getCoupleComparison(anchorDate: string, scope: CoupleScope) {
  return request<CoupleComparison>('GET', '/couples/comparisons', { query: { anchorDate, scope } });
}

/**
 * POST /couples/revoke — 동의 철회 · 연결 해제. 상대의 접근을 바로 끊고 이 커플의 대화와 리포트를 두 사람 것 모두 지워요(되돌릴 수 없어요).
 * 연결 전이면 만든 초대 코드를 취소해요. 연결이 없으면 아무 일도 없이 NONE이에요.
 */
export function revokeCouple() {
  return request<CoupleState>('POST', '/couples/revoke');
}
