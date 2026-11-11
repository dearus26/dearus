import { ApiError } from '@/api/errors';
import type { MockContext } from '@/api/mock/chat';
import { NOT_HANDLED, notFound, validationFailed } from '@/api/mock/helpers';
import type { components } from '@/api/schema';
import { addDays, isValidDateString, mondayOf, todayString } from '@/lib/date';

type MessengerUpload = components['schemas']['MessengerUpload'];

/**
 * 가짜 서버 중 메신저 올리기(txt · 이미지)예요. 규칙은 server/openapi.yaml을 따라요.
 * 올리면 PROCESSING이었다가 몇 초 뒤 DONE이 되고, 앞서 올린 게 분석 중이면 409예요.
 * DONE이 된 주(월요일)는 시점 고르기의 "메신저 N주" 기록량에 더해져요(chat.ts가 읽어 가요).
 */

const MAX_MESSAGES = 5000;
const MAX_TEXT = 2000;
const MAX_WEEKS = 60;
const MAX_IMAGES = 100;
/** 분석에 걸리는 시간(가짜). 실제 서버는 txt 8주가 30초 안쪽, 이미지 20장이 읽기 30초 + 요약이에요. */
const PROCESS_MS = { TXT: 3500, IMAGE: 7000 } as const;

type MockUpload = {
  id: string;
  format: MessengerUpload['format'];
  uploadedAt: string;
  photoCount: number;
  periodStart: string;
  periodEnd: string;
  /** 요약된 주의 월요일들 */
  weeks: string[];
  readyAt: number;
};

let uploads: MockUpload[] = [];
let uploadSeq = 0;

export function resetUploadMock() {
  uploads = [];
}

/** 분석이 끝난(DONE) 업로드들의 주(월요일). 시점 고르기의 메신저 기록량 계산에 써요. */
export function uploadedWeekStarts(): string[] {
  return uploads.filter((upload) => Date.now() >= upload.readyAt).flatMap((upload) => upload.weeks);
}

function toResponse(upload: MockUpload): MessengerUpload {
  const done = Date.now() >= upload.readyAt;
  const uploaded = new Date(upload.uploadedAt);
  const expires = new Date(uploaded);
  expires.setFullYear(expires.getFullYear() + 1);
  return {
    id: upload.id,
    format: upload.format,
    status: done ? 'DONE' : 'PROCESSING',
    uploadedAt: upload.uploadedAt,
    photoCount: upload.photoCount,
    periodStart: done ? upload.periodStart : null,
    periodEnd: done ? upload.periodEnd : null,
    weekCount: done ? upload.weeks.length : null,
    expiresAt: expires.toISOString(),
    failureCode: null,
  };
}

function checkReady(ctx: MockContext, input: { consent?: unknown }) {
  if (input.consent !== true) {
    throw new ApiError(400, 'CONSENT_REQUIRED', '위 내용을 확인하고 동의해야 올릴 수 있어요.');
  }
  if (!ctx.user.aiConsentActive) {
    throw new ApiError(403, 'AI_CONSENT_REQUIRED', 'AI 분석에 동의해야 쓸 수 있어요.');
  }
  if (uploads.some((upload) => Date.now() < upload.readyAt)) {
    throw new ApiError(409, 'UPLOAD_IN_PROGRESS', '앞서 올린 대화를 아직 분석하고 있어요. 끝난 뒤에 다시 올려 주세요.');
  }
}

function add(format: MockUpload['format'], photoCount: number, weeks: string[], periodStart: string, periodEnd: string): MockUpload {
  const now = Date.now();
  const upload: MockUpload = {
    id: `mock-upload-${++uploadSeq}`,
    format,
    uploadedAt: new Date(now).toISOString(),
    photoCount,
    periodStart,
    periodEnd,
    weeks,
    readyAt: now + PROCESS_MS[format],
  };
  uploads.push(upload);
  return upload;
}

/** 메신저 올리기 요청을 처리해요. 이 파일이 맡은 경로가 아니면 NOT_HANDLED를 돌려줘요. */
export function handleUploadRoute(method: string, path: string, body: unknown, ctx: MockContext): unknown {
  // POST /uploads/messenger — txt에서 추린 내 말
  if (method === 'POST' && path === '/uploads/messenger') {
    const input = (body ?? {}) as Partial<components['schemas']['MessengerUploadRequest']>;
    checkReady(ctx, input);
    if (input.source !== 'KAKAOTALK') {
      throw validationFailed([{ field: 'source', reason: '지금은 카카오톡만 받아요' }]);
    }
    const messages = Array.isArray(input.messages) ? input.messages : [];
    if (messages.length < 1 || messages.length > MAX_MESSAGES) {
      throw validationFailed([{ field: 'messages', reason: `1개 이상 ${MAX_MESSAGES}개 이하여야 해요` }]);
    }
    const today = todayString();
    for (const [index, message] of messages.entries()) {
      if (!isValidDateString(message.date) || message.date > today) {
        throw validationFailed([{ field: `messages[${index}].date`, reason: '올바른 지난 날짜여야 해요' }]);
      }
      if (typeof message.text !== 'string' || message.text.trim().length === 0 || message.text.length > MAX_TEXT) {
        throw validationFailed([{ field: `messages[${index}].text`, reason: `1자 이상 ${MAX_TEXT}자 이하여야 해요` }]);
      }
    }
    const dates = messages.map((message) => message.date).sort();
    const weeks = [...new Set(dates.map(mondayOf))].sort();
    if (weeks.length > MAX_WEEKS) {
      throw validationFailed([{ field: 'messages', reason: `한 번에 ${MAX_WEEKS}주(약 1년)까지만 올릴 수 있어요` }]);
    }
    return toResponse(add('TXT', 0, weeks, dates[0], dates[dates.length - 1]));
  }

  // POST /uploads/messenger/images — 스크린샷(가짜 서버는 장수만 받아요)
  if (method === 'POST' && path === '/uploads/messenger/images') {
    const input = (body ?? {}) as { source?: unknown; consent?: unknown; imageCount?: unknown };
    checkReady(ctx, input);
    const count = typeof input.imageCount === 'number' ? input.imageCount : 0;
    if (count < 1 || count > MAX_IMAGES) {
      throw validationFailed([{ field: 'images', reason: `1장 이상 ${MAX_IMAGES}장 이하여야 해요` }]);
    }
    // 읽은 대화를 8주로 꾸며요(실제 서버는 사진에서 날짜 구분선을 읽어요)
    const end = addDays(todayString(), -2);
    const start = addDays(end, -55);
    const weeks = Array.from({ length: 8 }, (_, index) => mondayOf(addDays(start, index * 7)));
    return toResponse(add('IMAGE', count, [...new Set(weeks)], start, end));
  }

  // GET /uploads/messenger — 목록(최신순)
  if (method === 'GET' && path === '/uploads/messenger') {
    return { items: [...uploads].reverse().map(toResponse) };
  }

  // GET · DELETE /uploads/messenger/{id}
  const match = /^\/uploads\/messenger\/([^/]+)$/.exec(path);
  if (match && (method === 'GET' || method === 'DELETE')) {
    const index = uploads.findIndex((upload) => upload.id === match[1]);
    if (index < 0) throw notFound();
    if (method === 'GET') return toResponse(uploads[index]);
    uploads.splice(index, 1);
    return undefined;
  }

  return NOT_HANDLED;
}
