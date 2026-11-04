import { request, requestStream } from '@/api/client';
import { ApiError, NETWORK_MESSAGE, UNKNOWN_MESSAGE } from '@/api/errors';
import type { components } from '@/api/schema';

export type ChatListItem = components['schemas']['ChatListItem'];
export type ChatSession = components['schemas']['ChatSession'];
export type ChatCreateInput = components['schemas']['ChatCreate'];
export type ChatMessage = components['schemas']['ChatMessage'];
export type ChatReply = components['schemas']['ChatReply'];
export type SafetyNotice = components['schemas']['SafetyNotice'];
export type EndChatResult = components['schemas']['EndChatResult'];
export type Report = components['schemas']['Report'];
export type ReportPending = components['schemas']['ReportPending'];

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

/** 한 번에 받을 수 있는 최대 개수(명세 limit 최대 200). 20턴이면 40개라서 한 번에 다 받아요. */
const MESSAGES_LIMIT = 200;

/** GET /chats/{id}/messages — seq 오름차순. afterSeq보다 큰 것만 받아서, 끊긴 뒤 이어받을 때도 써요. */
export function listMessages(chatId: string, afterSeq = 0) {
  return request<components['schemas']['MessageList']>('GET', `/chats/${encodeURIComponent(chatId)}/messages`, {
    query: { afterSeq, limit: MESSAGES_LIMIT },
  });
}

type SendHandlers = {
  /** 답변이 만들어지는 동안 조각마다 불려요. */
  onToken: (text: string) => void;
  /** 내 말에서 위기 신호가 감지되면 답변보다 먼저 한 번 불려요. */
  onSafety: (notice: SafetyNotice) => void;
  signal?: AbortSignal;
};

/**
 * POST /chats/{id}/messages — 내 말을 보내고 답변을 SSE로 받아요(token → safety → done · error).
 * 답변이 끝나 저장돼야 `done`이 와요. 실패하면 아무것도 저장되지 않아서 같은 글을 다시 보내면 돼요.
 *  - 시작 전 거절: 409 CHAT_NOT_READY · CHAT_ENDED · CHAT_BUSY, 403 AI_CONSENT_REQUIRED, 400 검증 실패
 *  - 도중 실패: SSE `error` 이벤트(AI_UNAVAILABLE 등)
 *  - 연결이 끊김: status 0인 ApiError. 서버는 답변을 끝까지 만들어 저장하니 listMessages로 이어받아요.
 */
export async function sendMessage(chatId: string, content: string, handlers: SendHandlers): Promise<ChatReply> {
  // 이벤트 처리 함수 안에서 채워지는 값이라서, 타입 검사가 헷갈리지 않게 객체에 담아요
  const outcome: { reply?: ChatReply; failure?: ApiError } = {};

  await requestStream(
    `/chats/${encodeURIComponent(chatId)}/messages`,
    { content },
    ({ event, data }) => {
      const payload = parseEventData(data);
      if (payload === null) return;
      if (event === 'token') handlers.onToken((payload as { text: string }).text);
      else if (event === 'safety') handlers.onSafety(payload as SafetyNotice);
      else if (event === 'done') outcome.reply = payload as ChatReply;
      else if (event === 'error') {
        const error = payload as { code?: string; message?: string; details?: Record<string, unknown> | null };
        outcome.failure = new ApiError(503, error.code ?? 'UNKNOWN', error.message ?? UNKNOWN_MESSAGE, error.details);
      }
    },
    handlers.signal,
  );

  if (outcome.failure) throw outcome.failure;
  // done이 오기 전에 스트림이 끝났다면 연결이 끊긴 거예요
  if (!outcome.reply) throw new ApiError(0, 'NETWORK_ERROR', NETWORK_MESSAGE);
  return outcome.reply;
}

function parseEventData(data: string): unknown {
  try {
    return JSON.parse(data);
  } catch {
    return null;
  }
}

/**
 * POST /chats/{id}/end — 대화를 끝내고 리포트 만들기를 시작해요(바로 돌아와요). 끝내야 종료돼요(자동 종료 없음).
 * 내 말이 2개 미만이면 reportStatus가 NONE이에요. 409 CHAT_BUSY(답변이 오는 중), 403 AI_CONSENT_REQUIRED, 503 SERVER_BUSY
 */
export function endChat(chatId: string) {
  return request<EndChatResult>('POST', `/chats/${encodeURIComponent(chatId)}/end`);
}

/**
 * GET /chats/{id}/report — 만드는 중이면 202로 { status: 'GENERATING' }이 와요(1~2초 뒤에 다시 불러요).
 * 404 CHAT_NOT_ENDED · REPORT_NOT_AVAILABLE(내 말이 2개 미만), 502 REPORT_FAILED(끝내기를 다시 부르면 다시 만들어요)
 */
export function getReport(chatId: string) {
  return request<Report | ReportPending>('GET', `/chats/${encodeURIComponent(chatId)}/report`);
}

export function isReportPending(report: Report | ReportPending): report is ReportPending {
  return 'status' in report;
}
