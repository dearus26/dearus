import { ApiError } from '@/api/errors';
import type { components } from '@/api/schema';

/** 가짜 서버(index.ts · chat.ts)가 함께 쓰는 작은 도우미들이에요. */

export const wait = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

export function validationFailed(fields: { field: string; reason: string }[]): ApiError {
  return new ApiError(400, 'VALIDATION_FAILED', '입력한 내용을 다시 확인해 주세요.', { fields });
}

export function notFound(): ApiError {
  return new ApiError(404, 'NOT_FOUND', '찾을 수 없어요.');
}

export function sortedEmotions(
  emotions: components['schemas']['Emotion'][] | undefined,
): components['schemas']['Emotion'][] {
  return Array.from(new Set(emotions ?? []));
}
