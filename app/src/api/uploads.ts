import { request } from '@/api/client';
import type { components } from '@/api/schema';

export type MessengerUpload = components['schemas']['MessengerUpload'];
export type MessengerMessage = components['schemas']['MessengerMessage'];

/**
 * POST /uploads/messenger — txt에서 추린 내 말을 올려요(202). 상대 말은 폰에서 버리고, 글자를 가리지 않아요.
 * 동의 체크는 처음 한 번 받고 폰에 기억해 둬서(features/messenger/consentMemory, 2026.11.28) consent는 항상 true예요(동의한 뒤에만 부르는 함수예요).
 * 400 CONSENT_REQUIRED · VALIDATION_FAILED, 403 AI_CONSENT_REQUIRED, 409 UPLOAD_IN_PROGRESS(앞서 올린 게 분석 중), 413 용량 초과
 */
export function uploadMessengerTxt(messages: MessengerMessage[]) {
  return request<MessengerUpload>('POST', '/uploads/messenger', {
    body: { source: 'KAKAOTALK', consent: true, messages } satisfies components['schemas']['MessengerUploadRequest'],
  });
}

/** GET /uploads/messenger/{id} — 분석 상태. PROCESSING이면 1~2초 뒤에 다시 불러요. */
export function getMessengerUpload(id: string) {
  return request<MessengerUpload>('GET', `/uploads/messenger/${encodeURIComponent(id)}`);
}

/** GET /uploads/messenger — 올린 대화 목록(최신순). 설정의 "내 데이터 보기"에서 써요. */
export function listMessengerUploads() {
  return request<{ items: MessengerUpload[] }>('GET', '/uploads/messenger');
}

/** DELETE /uploads/messenger/{id} — 이 업로드의 내 말 원문과 주간 요약을 지워요. */
export function deleteMessengerUpload(id: string) {
  return request<void>('DELETE', `/uploads/messenger/${encodeURIComponent(id)}`);
}
