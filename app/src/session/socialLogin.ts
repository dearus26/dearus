import type { Provider } from '@/api/auth';

export type SocialProvider = 'kakao' | 'google' | 'apple';

export type SocialCredential = {
  /** POST /auth/{provider}의 경로에 들어갈 값 */
  provider: Provider;
  /** 소셜 SDK가 준 토큰 (provider마다 종류가 달라요) */
  token: string;
  /** 애플처럼 처음 한 번만 이름을 주는 경우에만 */
  nickname?: string;
};

/**
 * 소셜 로그인 SDK에서 서버에 보낼 토큰을 받아오는 자리예요.
 *
 * 지금은 가짜예요(AGENTS.md "로그인" 결정): 서버의 개발용 로그인(`dev`)에 보낼 토큰만 만들어요.
 * 같은 버튼은 늘 같은 사용자가 돼요. 실제 연동(11월 1~2주, 개발 빌드)에서는 이 함수 안만 바꿔요 —
 *   kakao  → 카카오 SDK의 access token
 *   google → 구글 로그인의 ID token
 *   apple  → 애플 로그인의 identity token
 */
export async function getSocialCredential(provider: SocialProvider): Promise<SocialCredential> {
  return { provider: 'dev', token: `app-dev-${provider}` };
}
