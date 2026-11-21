import { isRunningInExpoGo } from 'expo';

import type { Provider } from '@/api/auth';
import { ApiError } from '@/api/errors';

export type SocialProvider = 'kakao' | 'google' | 'apple';

export type SocialCredential = {
  /** POST /auth/{provider}의 경로에 들어갈 값 */
  provider: Provider;
  /** 소셜 SDK가 준 토큰 (provider마다 종류가 달라요) */
  token: string;
  /** 애플처럼 처음 한 번만 이름을 주는 경우에만 */
  nickname?: string;
};

/** 사용자가 로그인 창을 닫았어요. 오류가 아니라서 화면에 알리지 않아요. */
export class SocialLoginCancelled extends Error {
  constructor() {
    super('로그인을 취소했어요');
    this.name = 'SocialLoginCancelled';
  }
}

const NOT_CONFIGURED = '로그인 설정이 아직 끝나지 않았어요.';
const FAILED = '로그인하지 못했어요. 잠시 뒤에 다시 해 주세요.';

function loginError(message: string) {
  return new ApiError(0, 'SOCIAL_LOGIN_FAILED', message);
}

/**
 * 소셜 로그인 SDK에서 서버에 보낼 토큰을 받아와요.
 *
 * 가짜 데이터(mock)와 Expo Go에서는 네이티브 SDK를 쓸 수 없어서 개발용 로그인(`dev`) 토큰을 만들어요.
 * 개발용 로그인은 각자 PC의 서버에서만 켜져 있고, 배포 서버에서는 꺼져 있어요.
 * 개발 빌드(APK)에서 실제 서버에 붙으면 진짜 로그인을 해요.
 *   kakao  → 카카오 SDK의 access token
 *   google → 구글 로그인의 ID token (웹 클라이언트 ID로 발급)
 *   apple  → 아직 없어요. 서버가 501로 "준비 중"을 알려줘요(2026.11.18 결정)
 */
export async function getSocialCredential(provider: SocialProvider): Promise<SocialCredential> {
  if (process.env.EXPO_PUBLIC_API_MODE !== 'live' || isRunningInExpoGo()) {
    return { provider: 'dev', token: `app-dev-${provider}` };
  }
  switch (provider) {
    case 'kakao':
      return { provider, token: await kakaoAccessToken() };
    case 'google':
      return { provider, token: await googleIdToken() };
    case 'apple':
      return { provider, token: 'not-ready' };
  }
}

let kakaoReady = false;

/** 네이티브 모듈은 Expo Go에서 불러오면 앱이 깨져서, 쓸 때만 불러와요. */
async function kakaoAccessToken(): Promise<string> {
  const appKey = process.env.EXPO_PUBLIC_KAKAO_NATIVE_APP_KEY;
  if (!appKey) {
    throw loginError(NOT_CONFIGURED);
  }
  const [{ initializeKakaoSDK }, { login }] = await Promise.all([
    import('@react-native-kakao/core'),
    import('@react-native-kakao/user'),
  ]);
  try {
    if (!kakaoReady) {
      await initializeKakaoSDK(appKey);
      kakaoReady = true;
    }
    const { accessToken } = await login();
    return accessToken;
  } catch (error) {
    const code = (error as { code?: unknown } | null)?.code;
    if (code === 'Cancelled' || code === 'AccessDenied') {
      throw new SocialLoginCancelled();
    }
    throw loginError(FAILED);
  }
}

let googleReady = false;

async function googleIdToken(): Promise<string> {
  const webClientId = process.env.EXPO_PUBLIC_GOOGLE_WEB_CLIENT_ID;
  if (!webClientId) {
    throw loginError(NOT_CONFIGURED);
  }
  const { GoogleSignin } = await import('@react-native-google-signin/google-signin');
  let result: Awaited<ReturnType<typeof GoogleSignin.signIn>>;
  try {
    if (!googleReady) {
      GoogleSignin.configure({ webClientId });
      googleReady = true;
    }
    await GoogleSignin.hasPlayServices({ showPlayServicesUpdateDialog: true });
    // 앱에서 로그아웃한 뒤 다른 계정을 고를 수 있게, 전에 고른 계정을 잊고 시작해요
    await GoogleSignin.signOut();
    result = await GoogleSignin.signIn();
  } catch {
    throw loginError(FAILED);
  }
  if (result.type === 'cancelled') {
    throw new SocialLoginCancelled();
  }
  if (!result.data.idToken) {
    throw loginError(FAILED);
  }
  return result.data.idToken;
}
