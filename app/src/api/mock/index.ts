import { ApiError, UNAUTHORIZED_MESSAGE } from '@/api/errors';
import type { components } from '@/api/schema';

type User = components['schemas']['User'];
type AuthResponse = components['schemas']['AuthResponse'];

/**
 * 서버 없이 앱을 만들 때 쓰는 가짜 서버예요(EXPO_PUBLIC_API_MODE=mock).
 * 요청 · 응답 모양은 server/openapi.yaml을 따라요. 화면은 이걸 몰라요 — api/ 함수만 불러요.
 * 메모리에만 있어서 앱을 완전히 다시 켜면 처음 상태로 돌아가요.
 */

const MOCK_TOKEN = 'mock-access-token';
const DELAY_MS = 350;
const RETENTION_DAYS = 30;

let user: User | null = null;

const wait = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

function newUser(provider: User['provider']): User {
  return {
    id: '00000000-0000-4000-8000-000000000001',
    provider,
    nickname: '서연',
    aiConsentedAt: null,
    aiRevokedAt: null,
    aiDataDeleteAt: null,
    aiConsentActive: false,
    needsConsent: true,
    notificationsOptIn: false,
    createdAt: new Date().toISOString(),
  };
}

function validationFailed(fields: { field: string; reason: string }[]): ApiError {
  return new ApiError(400, 'VALIDATION_FAILED', '입력한 내용을 다시 확인해 주세요.', { fields });
}

export async function mockRequest<T>(
  method: string,
  path: string,
  body: unknown,
  token: string | null,
): Promise<T> {
  await wait(DELAY_MS);

  // POST /auth/{provider} — 로그인
  const login = /^\/auth\/(kakao|google|apple|dev)$/.exec(path);
  if (method === 'POST' && login) {
    const isNewUser = user === null;
    user = user ?? newUser(login[1] as User['provider']);
    const response: AuthResponse = {
      accessToken: MOCK_TOKEN,
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString(),
      isNewUser,
      user,
    };
    return response as T;
  }

  // 여기부터는 로그인이 필요해요
  if (token !== MOCK_TOKEN) {
    throw new ApiError(401, 'UNAUTHORIZED', UNAUTHORIZED_MESSAGE);
  }
  // 앱을 껐다 켜서 가짜 서버가 초기화됐어도 저장된 토큰으로 이어서 쓰게 해요
  user = user ?? newUser('dev');

  // GET /me
  if (method === 'GET' && path === '/me') {
    return user as T;
  }

  // PUT /me/consent — 서비스 이용 동의
  if (method === 'PUT' && path === '/me/consent') {
    const input = (body ?? {}) as Partial<components['schemas']['UserConsentRequest']>;
    const missing = (['terms', 'privacy', 'aiAnalysis'] as const)
      .filter((key) => input[key] !== true)
      .map((field) => ({ field, reason: '꼭 동의해야 해요' }));
    if (missing.length > 0) throw validationFailed(missing);

    // 이미 유효한 동의에 다시 불러도 시각은 그대로 두고 알림 설정만 바꿔요
    if (!user.aiConsentActive) {
      user = { ...user, aiConsentedAt: new Date().toISOString(), aiRevokedAt: null, aiDataDeleteAt: null };
    }
    user = {
      ...user,
      aiConsentActive: true,
      needsConsent: false,
      notificationsOptIn: input.notifications ?? user.notificationsOptIn,
    };
    return user as T;
  }

  // DELETE /me/consent — AI 분석 동의 철회 (분석 데이터는 30일 뒤 삭제)
  if (method === 'DELETE' && path === '/me/consent') {
    if (user.aiConsentActive) {
      const now = new Date();
      user = {
        ...user,
        aiConsentActive: false,
        aiRevokedAt: now.toISOString(),
        aiDataDeleteAt: new Date(now.getTime() + RETENTION_DAYS * 24 * 60 * 60 * 1000).toISOString(),
      };
    }
    return user as T;
  }

  // DELETE /me — 회원 탈퇴
  if (method === 'DELETE' && path === '/me') {
    user = null;
    return undefined as T;
  }

  throw new ApiError(404, 'NOT_FOUND', '찾을 수 없어요.');
}
