import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useFonts } from 'expo-font';
import { SplashScreen, Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useEffect } from 'react';

import { SessionProvider, useSession } from '@/session/SessionProvider';
import { colors } from '@/theme/colors';
import { fontAssets } from '@/theme/fonts';

// 글꼴과 로그인 상태를 확인할 때까지 스플래시 화면을 계속 보여줘요.
SplashScreen.preventAutoHideAsync();

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, staleTime: 30_000 } },
});

export default function RootLayout() {
  const [fontsLoaded, fontError] = useFonts(fontAssets);
  if (!fontsLoaded && !fontError) return null;

  return (
    <QueryClientProvider client={queryClient}>
      <SessionProvider>
        <SplashGate />
        <StatusBar style="dark" />
        <RootNavigator />
      </SessionProvider>
    </QueryClientProvider>
  );
}

/** 저장된 토큰 확인이 끝나면 스플래시를 걷어요. */
function SplashGate() {
  const { status } = useSession();
  useEffect(() => {
    if (status !== 'loading') SplashScreen.hide();
  }, [status]);
  return null;
}

/**
 * 로그인 상태에 따라 보여줄 화면을 정해요(Stack.Protected).
 *  - 로그인 전            → login
 *  - 로그인 + 약관 동의 전 → consent
 *  - 로그인 + 동의 완료    → (tabs)
 * 상태가 바뀌면 열려 있던 화면이 막히면서 알아서 다음 화면으로 넘어가요.
 */
function RootNavigator() {
  const { status, user } = useSession();
  const signedIn = status === 'signedIn' && user !== null;
  const needsConsent = user?.needsConsent ?? false;

  return (
    <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.cream } }}>
      <Stack.Protected guard={!signedIn}>
        <Stack.Screen name="login" />
      </Stack.Protected>
      <Stack.Protected guard={signedIn && needsConsent}>
        <Stack.Screen name="consent" />
      </Stack.Protected>
      <Stack.Protected guard={signedIn && !needsConsent}>
        <Stack.Screen name="(tabs)" />
        <Stack.Screen name="diary/[date]" />
        <Stack.Screen name="persona/pick" />
        <Stack.Screen name="chat/[id]" />
        <Stack.Screen name="settings" />
      </Stack.Protected>
      {/* 약관 문서는 로그인 상태와 상관없이 볼 수 있어요 */}
      <Stack.Screen name="legal/[type]" />
    </Stack>
  );
}
