import type { ReactNode } from 'react';
import { ActivityIndicator, Pressable, StyleSheet } from 'react-native';

import { AppText } from '@/components/AppText';
import { colors } from '@/theme/colors';

export type ButtonVariant = 'gold' | 'brown' | 'line' | 'kakao' | 'google' | 'apple';

/** 스토리보드의 .btn 변형들이에요. */
const variants: Record<ButtonVariant, { background: string; foreground: string; border?: string }> = {
  gold: { background: colors.gold, foreground: colors.ink },
  brown: { background: colors.brown, foreground: colors.cream },
  line: { background: 'transparent', foreground: colors.brown, border: colors.beige },
  kakao: { background: colors.kakao, foreground: colors.kakaoText },
  google: { background: colors.white, foreground: colors.googleText, border: colors.beige },
  apple: { background: colors.black, foreground: colors.white },
};

type Props = {
  label: string;
  onPress: () => void;
  variant?: ButtonVariant;
  /** 글자 바로 앞에 붙는 아이콘(소셜 로그인 로고 등) */
  icon?: ReactNode;
  disabled?: boolean;
  loading?: boolean;
};

export function Button({ label, onPress, variant = 'gold', icon, disabled = false, loading = false }: Props) {
  const v = variants[variant];
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled, busy: loading }}
      disabled={disabled || loading}
      onPress={onPress}
      style={({ pressed }) => [
        styles.base,
        {
          backgroundColor: v.background,
          borderColor: v.border ?? 'transparent',
          borderWidth: v.border ? 1.5 : 0,
          opacity: disabled ? 0.45 : pressed ? 0.85 : 1,
        },
      ]}>
      {loading ? (
        <ActivityIndicator color={v.foreground} />
      ) : (
        <>
          {icon}
          <AppText weight="bold" size={16} color={v.foreground}>
            {label}
          </AppText>
        </>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    height: 52,
    borderRadius: 16,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    alignSelf: 'stretch',
  },
});
