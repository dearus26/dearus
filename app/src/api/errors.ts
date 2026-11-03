/**
 * 서버가 돌려주는 { code, message, details } 오류예요(명세 공통 규칙).
 * 화면은 `code`로 분기하고, `message`는 그대로 보여줘도 되는 해요체 문장이에요.
 * status가 0이면 서버에 닿지 못한 경우(네트워크 오류 · 시간 초과)예요.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: Record<string, unknown> | null;

  constructor(status: number, code: string, message: string, details?: Record<string, unknown> | null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details ?? null;
  }
}

export const UNAUTHORIZED_MESSAGE = '로그인이 만료됐어요. 다시 로그인해 주세요.';
export const NETWORK_MESSAGE = '서버에 연결하지 못했어요. 네트워크를 확인해 주세요.';
export const UNKNOWN_MESSAGE = '문제가 생겼어요. 잠시 뒤에 다시 해 주세요.';

/** catch로 잡은 값에서 화면에 보여줄 문장을 꺼내요. */
export function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : UNKNOWN_MESSAGE;
}
