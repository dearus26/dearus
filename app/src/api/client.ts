import { ApiError, NETWORK_MESSAGE, UNAUTHORIZED_MESSAGE, UNKNOWN_MESSAGE } from '@/api/errors';
import { mockRequest } from '@/api/mock';

/**
 * 서버(Spring Boot) 호출의 유일한 입구예요. 화면은 fetch를 직접 쓰지 않고 api/ 함수만 불러요.
 *  - EXPO_PUBLIC_API_MODE=mock(기본): 가짜 서버(api/mock)로 동작해요.
 *  - EXPO_PUBLIC_API_MODE=live: EXPO_PUBLIC_API_URL의 실제 서버를 불러요.
 */
const API_MODE: 'mock' | 'live' = process.env.EXPO_PUBLIC_API_MODE === 'live' ? 'live' : 'mock';
const API_URL = (process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8080').replace(/\/+$/, '');
const TIMEOUT_MS = 15_000;

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
  const json = parseJson(text);

  if (!response.ok) {
    // 서버가 토큰 없는 401은 본문 없이 돌려줄 때가 있어서, 401은 상태 코드로 판단해요
    if (response.status === 401) {
      throw new ApiError(401, 'UNAUTHORIZED', json?.message ?? UNAUTHORIZED_MESSAGE);
    }
    throw new ApiError(response.status, json?.code ?? 'UNKNOWN', json?.message ?? UNKNOWN_MESSAGE, json?.details);
  }
  return json as T;
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
