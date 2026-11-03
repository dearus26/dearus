import { useQueryClient } from '@tanstack/react-query';
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';

import { getMe, giveConsent as giveConsentApi, login, type User } from '@/api/auth';
import { setAccessToken, setUnauthorizedHandler } from '@/api/client';
import { ApiError } from '@/api/errors';
import { getSocialCredential, type SocialProvider } from '@/session/socialLogin';
import { clearToken, readToken, saveToken } from '@/session/tokenStorage';

/** loading: 저장된 토큰을 확인하는 중(스플래시가 덮고 있어요) */
export type SessionStatus = 'loading' | 'signedOut' | 'signedIn';

type SessionValue = {
  status: SessionStatus;
  /** signedIn일 때만 있어요 */
  user: User | null;
  signIn: (provider: SocialProvider) => Promise<void>;
  /** 서비스 이용 동의(필수 3개) + 알림 선택. 성공하면 needsConsent가 false가 돼요. */
  giveConsent: (notifications: boolean) => Promise<void>;
  signOut: () => Promise<void>;
};

const SessionContext = createContext<SessionValue | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<SessionStatus>('loading');
  const [user, setUser] = useState<User | null>(null);

  async function endSession() {
    await clearToken();
    setAccessToken(null);
    queryClient.clear();
    setUser(null);
    setStatus('signedOut');
  }

  // 앱을 켤 때 저장된 토큰이 아직 유효한지 GET /me로 확인해요.
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const token = await readToken();
      if (!token) {
        if (!cancelled) setStatus('signedOut');
        return;
      }
      setAccessToken(token);
      try {
        const me = await getMe();
        if (cancelled) return;
        setUser(me);
        setStatus('signedIn');
      } catch (error) {
        if (cancelled) return;
        // 토큰이 만료됐으면(401) 지우고, 네트워크 문제면 토큰은 두고 로그인 화면으로 가요
        if (error instanceof ApiError && error.status === 401) await clearToken();
        setAccessToken(null);
        setStatus('signedOut');
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 앱을 켤 때 한 번만 해요
  }, []);

  // 로그인한 뒤 어떤 요청이든 401을 받으면(토큰 만료 · 탈퇴) 로그인 화면으로 보내요.
  useEffect(() => {
    setUnauthorizedHandler(() => {
      void endSession();
    });
    return () => setUnauthorizedHandler(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- endSession은 매번 새로 만들어지지만 하는 일은 같아요
  }, []);

  async function signIn(provider: SocialProvider) {
    const credential = await getSocialCredential(provider);
    const response = await login(credential.provider, credential.token, credential.nickname);
    await saveToken(response.accessToken);
    setAccessToken(response.accessToken);
    setUser(response.user);
    setStatus('signedIn');
  }

  async function giveConsent(notifications: boolean) {
    const updated = await giveConsentApi({ terms: true, privacy: true, aiAnalysis: true, notifications });
    setUser(updated);
  }

  return (
    <SessionContext.Provider value={{ status, user, signIn, giveConsent, signOut: endSession }}>
      {children}
    </SessionContext.Provider>
  );
}

export function useSession(): SessionValue {
  const value = useContext(SessionContext);
  if (!value) throw new Error('useSession은 SessionProvider 안에서만 쓸 수 있어요.');
  return value;
}
