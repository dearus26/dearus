import { request } from '@/api/client';
import type { components } from '@/api/schema';

export type MessengerUpload = components['schemas']['MessengerUpload'];
export type MessengerMessage = components['schemas']['MessengerMessage'];

/** 폰 갤러리에서 고른 대화 캡처 한 장. 사진은 가공하지 않고 그대로 서버로 보내요. */
export type PickedImage = {
  uri: string;
  name: string;
  /** image/jpeg · image/png · image/webp */
  mimeType: string;
  /** 파일 크기(바이트). 폰이 알려줄 때만 있어요(한 장 5MB · 전체 50MB 한도를 올리기 전에 확인해요) */
  size?: number;
};

/**
 * POST /uploads/messenger — txt에서 추린 내 말을 올려요(202). 상대 말은 폰에서 버리고, 글자를 가리지 않아요.
 * 올릴 때마다 동의 체크를 한 번 더 확인해서 consent는 항상 true예요(사용자가 체크한 뒤에만 부르는 함수예요).
 * 400 CONSENT_REQUIRED · VALIDATION_FAILED, 403 AI_CONSENT_REQUIRED, 409 UPLOAD_IN_PROGRESS(앞서 올린 게 분석 중), 413 용량 초과
 */
export function uploadMessengerTxt(messages: MessengerMessage[]) {
  return request<MessengerUpload>('POST', '/uploads/messenger', {
    body: { source: 'KAKAOTALK', consent: true, messages } satisfies components['schemas']['MessengerUploadRequest'],
  });
}

/**
 * POST /uploads/messenger/images — 대화 스크린샷을 multipart로 올려요(202). 대화 순서대로(오래된 것부터) 보내요.
 * 서버가 AI로 내 말(오른쪽 · 노란색 말풍선)을 읽고 사진은 바로 지워요. 한 번에 1~100장, 한 장 5MB, 전체 50MB, JPEG · PNG · WebP예요.
 * 가짜 서버는 사진 파일을 다루지 않아서 장수만 body로 받아요(실제 서버는 아래 폼을 보내요).
 */
export function uploadMessengerImages(images: PickedImage[]) {
  return request<MessengerUpload>('POST', '/uploads/messenger/images', {
    body: { source: 'KAKAOTALK', consent: true, imageCount: images.length },
    multipart: () => {
      const form = new FormData();
      form.append('source', 'KAKAOTALK');
      form.append('consent', 'true');
      for (const image of images) {
        // React Native의 FormData는 { uri, name, type } 객체를 파일로 보내요
        form.append('images', { uri: image.uri, name: image.name, type: image.mimeType } as unknown as Blob);
      }
      return form;
    },
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
