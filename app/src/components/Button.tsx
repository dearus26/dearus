import type { ReactNode } from 'react';
import { ActivityIndicator, Pressable, StyleSheet } from 'react-native';

import { AppText } from '@/components/AppText';
import { colors, emotionColors } from '@/theme/colors';

export type ButtonVariant = 'gold' | 'brown' | 'line' | 'danger' | 'kakao' | 'google' | 'apple';

/** 스토리보드의 .btn 변형들이에요. danger는 되돌릴 수 없는 일(탈퇴 · 철회)을 확인하는 버튼이에요. */
const variants: Record<ButtonVariant, { background: string; foreground: string; border?: string }> = {
  gold: { background: colors.gold, foreground: colors.ink },
  brown: { background: colors.brown, foreground: colors.cream },
  line: { background: 'transparent', foreground: colors.brown, border: colors.beige },
  danger: { background: emotionColors.ANGRY, foreground: colors.white },
  kakao: { background: colors.kakao, foreground: colors.kakaoText },
  google: { background: colors.white, foreground: colors.googleText, border: colors.beige },
  apple: { background: colors.black, foreground: colors.white },
};

/** regular: 화면 아래의 큰 버튼, small: 패널 안에서 여러 개를 나란히 둘 때 */
const sizes = {
  regular: { height: 52, radius: 16, fontSize: 16 },
  small: { height: 40, radius: 12, fontSize: 13.5 },
} as const;

type Props = {
  label: string;
  onPress: () => void;
  variant?: ButtonVariant;
  size?: keyof typeof sizes;
  /** 글자 바로 앞에 붙는 아이콘(소셜 로그인 로고 등) */
  icon?: ReactNode;
  disabled?: boolean;
  loading?: boolean;
};

export function Button({
  label,
  onPress,
  variant = 'gold',
  size = 'regular',
  icon,
  disabled = false,
  loading = false,
}: Props) {
  const v = variants[variant];
  const s = sizes[size];
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled, busy: loading }}
      disabled={disabled || loading}
      onPress={onPress}
      style={({ pressed }) => [
        styles.base,
        {
          height: s.height,
          borderRadius: s.radius,
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
          <AppText weight="bold" size={s.fontSize} lineHeight={Math.round(s.fontSize * 1.4)} color={v.foreground}>
            {label}
          </AppText>
        </>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    alignSelf: 'stretch',
  },
});
