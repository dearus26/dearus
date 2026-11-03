import { useState } from 'react';
import { Alert, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { errorMessage } from '@/api/errors';
import { AppText } from '@/components/AppText';
import { BrandLogo } from '@/components/BrandLogo';
import { Button } from '@/components/Button';
import { useSession } from '@/session/SessionProvider';
import type { SocialProvider } from '@/session/socialLogin';
import { colors } from '@/theme/colors';

/** 스토리보드 A-1 로그인 */
export default function LoginScreen() {
  const insets = useSafeAreaInsets();
  const { signIn } = useSession();
  const [pending, setPending] = useState<SocialProvider | null>(null);

  async function handleLogin(provider: SocialProvider) {
    setPending(provider);
    try {
      await signIn(provider);
    } catch (error) {
      Alert.alert('로그인하지 못했어요', errorMessage(error));
    } finally {
      setPending(null);
    }
  }

  return (
    <View style={[styles.screen, { paddingTop: insets.top + 70, paddingBottom: Math.max(insets.bottom, 12) + 24 }]}>
      <View style={styles.hero}>
        <View style={styles.orb}>
          <View style={styles.orbDashed} />
          <View style={styles.orbFilled} />
        </View>
        <AppText serif weight="bold" size={40} lineHeight={52} style={styles.brand}>
          Dear Us
        </AppText>
        <AppText size={15} lineHeight={24} color={colors.brown} style={styles.tagline}>
          {'과거의 나, 그리고 그때의 우리와\n다시 대화하는 회고 일기'}
        </AppText>
      </View>

      <View style={styles.actions}>
        <Button
          label="카카오로 시작하기"
          variant="kakao"
          icon={<BrandLogo brand="kakao" />}
          onPress={() => handleLogin('kakao')}
          loading={pending === 'kakao'}
          disabled={pending !== null && pending !== 'kakao'}
        />
        <Button
          label="Google로 시작하기"
          variant="google"
          icon={<BrandLogo brand="google" />}
          onPress={() => handleLogin('google')}
          loading={pending === 'google'}
          disabled={pending !== null && pending !== 'google'}
        />
        <Button
          label="Apple로 계속하기"
          variant="apple"
          icon={<BrandLogo brand="apple" />}
          onPress={() => handleLogin('apple')}
          loading={pending === 'apple'}
          disabled={pending !== null && pending !== 'apple'}
        />
        <AppText size={12.5} lineHeight={19} color={colors.brown} style={styles.fine}>
          {'처음 시작하면 다음 화면에서\n이용약관 동의가 필요해요'}
        </AppText>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: colors.cream,
    paddingHorizontal: 28,
  },
  hero: {
    alignItems: 'center',
  },
  // 스토리보드 .orb: 점선 원과 금색 원이 겹쳐 있어요
  orb: {
    width: 120,
    height: 80,
  },
  orbDashed: {
    position: 'absolute',
    left: 10,
    top: 8,
    width: 64,
    height: 64,
    borderRadius: 32,
    borderWidth: 2,
    borderStyle: 'dashed',
    borderColor: colors.brown,
    opacity: 0.7,
  },
  orbFilled: {
    position: 'absolute',
    left: 44,
    top: 8,
    width: 64,
    height: 64,
    borderRadius: 32,
    backgroundColor: colors.gold,
  },
  brand: {
    marginTop: 10,
    letterSpacing: 0.4,
    textAlign: 'center',
  },
  tagline: {
    marginTop: 10,
    textAlign: 'center',
  },
  actions: {
    marginTop: 'auto',
    gap: 12,
  },
  fine: {
    textAlign: 'center',
    marginTop: 6,
  },
});
