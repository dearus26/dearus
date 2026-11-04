import { fetch as streamFetch } from 'expo/fetch';

import { ApiError, NETWORK_MESSAGE, UNAUTHORIZED_MESSAGE, UNKNOWN_MESSAGE } from '@/api/errors';
import { mockRequest, mockStreamRequest } from '@/api/mock';
import { createSseParser, type SseEvent } from '@/api/sse';

/**
 * 서버(Spring Boot) 호출의 유일한 입구예요. 화면은 fetch를 직접 쓰지 않고 api/ 함수만 불러요.
 *  - EXPO_PUBLIC_API_MODE=mock(기본): 가짜 서버(api/mock)로 동작해요.
 *  - EXPO_PUBLIC_API_MODE=live: EXPO_PUBLIC_API_URL의 실제 서버를 불러요.
 */
const API_MODE: 'mock' | 'live' = process.env.EXPO_PUBLIC_API_MODE === 'live' ? 'live' : 'mock';
const API_URL = (process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8080').replace(/\/+$/, '');
const TIMEOUT_MS = 15_000;
/** 답변을 스트림으로 받는 요청은 일반 요청보다 오래 걸려서, 시작부터 끝까지 이만큼까지 기다려요. */
const STREAM_TIMEOUT_MS = 60_000;

export const isMockApi = API_MODE === 'mock';

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';

type Options = {
  body?: unknown;
  /** 로그인 API처럼 토큰 없이 부를 때만 false */
  auth?: boolean;
  query?: Record<string, string | number | undefined>;
};

let accessToken: string | null = null;
let unauthorizedHandler: (() => void) | null = null;

export function setAccessToken(token: string | null) {
  accessToken = token;
}

/** 토큰이 있는데 401을 받으면(만료 · 탈퇴) 불러줘요. 세션 쪽에서 로그아웃 처리를 연결해요. */
export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler;
}

export async function request<T>(method: Method, path: string, options: Options = {}): Promise<T> {
  const { body, auth = true, query } = options;
  const token = auth ? accessToken : null;
  const fullPath = path + toQueryString(query);
  try {
    return API_MODE === 'mock'
      ? await mockRequest<T>(method, fullPath, body, token)
      : await liveRequest<T>(method, fullPath, body, token);
  } catch (error) {
    if (auth && token && error instanceof ApiError && error.status === 401) {
      unauthorizedHandler?.();
    }
    throw error;
  }
}

/**
 * 답변을 SSE로 받는 POST 요청이에요(채팅 답변). 이벤트가 올 때마다 onEvent를 불러요.
 * 시작도 못 하고 거절당하면(409 CHAT_BUSY 등) ApiError를 던지고, 도중에 연결이 끊기면 status 0인 ApiError를 던져요.
 * React Native 기본 fetch는 스트림이 불안정해서 `expo/fetch`로 받아요.
 */
export async function requestStream(
  path: string,
  body: unknown,
  onEvent: (event: SseEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const token = accessToken;
  try {
    if (API_MODE === 'mock') {
      await mockStreamRequest(path, body, token, onEvent, signal);
    } else {
      await liveStream(path, body, token, onEvent, signal);
    }
  } catch (error) {
    if (token && error instanceof ApiError && error.status === 401) {
      unauthorizedHandler?.();
    }
    throw error;
  }
}

function toQueryString(query: Options['query']): string {
  if (!query) return '';
  const params = Object.entries(query)
    .filter((entry): entry is [string, string | number] => entry[1] !== undefined)
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`);
  return params.length > 0 ? `?${params.join('&')}` : '';
}

async function liveRequest<T>(method: Method, path: string, body: unknown, token: string | null): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
  let response: Response;
  try {
    response = await fetch(`${API_URL}${path}`, {
      method,
      headers: {
        Accept: 'application/json',
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    });
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', NETWORK_MESSAGE);
  } finally {
    clearTimeout(timer);
  }

  if (response.status === 204) return undefined as T;

  const text = await response.text();
  if (!response.ok) throw errorFromResponse(response.status, text);
  return parseJson(text) as T;
}

async function liveStream(
  path: string,
  body: unknown,
  token: string | null,
  onEvent: (event: SseEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  // 화면을 벗어나서 caller가 멈추라고 하거나, 너무 오래 걸리면 끊어요
  const controller = new AbortController();
  const stop = () => controller.abort();
  signal?.addEventListener('abort', stop);
  const timer = setTimeout(stop, STREAM_TIMEOUT_MS);
  try {
    const response = await streamFetch(`${API_URL}${path}`, {
      method: 'POST',
      headers: {
        Accept: 'text/event-stream',
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify(body),
      signal: controller.signal,
    });
    if (!response.ok) throw errorFromResponse(response.status, await response.text());
    if (!response.body) throw new ApiError(0, 'NETWORK_ERROR', NETWORK_MESSAGE);

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    const parser = createSseParser(onEvent);
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      parser.push(decoder.decode(value, { stream: true }));
    }
  } catch (error) {
    // 시작 전의 거절(ApiError)은 그대로 알리고, 그 밖에(끊김 · 시간 초과)는 네트워크 오류로 알려요
    throw error instanceof ApiError ? error : new ApiError(0, 'NETWORK_ERROR', NETWORK_MESSAGE);
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener('abort', stop);
  }
}

/** 오류 응답 본문 { code, message, details }로 ApiError를 만들어요. */
function errorFromResponse(status: number, text: string): ApiError {
  const json = parseJson(text);
  // 서버가 토큰 없는 401은 본문 없이 돌려줄 때가 있어서, 401은 상태 코드로 판단해요
  if (status === 401) return new ApiError(401, 'UNAUTHORIZED', json?.message ?? UNAUTHORIZED_MESSAGE);
  return new ApiError(status, json?.code ?? 'UNKNOWN', json?.message ?? UNKNOWN_MESSAGE, json?.details);
}

type ErrorBody = { code?: string; message?: string; details?: Record<string, unknown> | null };

function parseJson(text: string): (ErrorBody & Record<string, unknown>) | null {
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}
