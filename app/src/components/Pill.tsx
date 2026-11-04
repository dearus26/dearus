import { ActivityIndicator, Pressable, StyleSheet } from 'react-native';

import { AppText } from '@/components/AppText';
import { colors } from '@/theme/colors';

type Props = {
  label: string;
  onPress: () => void;
  /** gold: 채운 알약(저장 등), line: 테두리 알약(메신저 올리기 등) */
  tone?: 'gold' | 'line';
  disabled?: boolean;
  loading?: boolean;
};

/** 스토리보드의 .pill — 작은 알약 모양 버튼 */
export function Pill({ label, onPress, tone = 'gold', disabled = false, loading = false }: Props) {
  const line = tone === 'line';
  const foreground = line ? colors.brown : colors.ink;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled, busy: loading }}
      disabled={disabled || loading}
      hitSlop={6}
      onPress={onPress}
      style={({ pressed }) => [
        styles.pill,
        line ? styles.line : styles.gold,
        { opacity: disabled ? 0.45 : pressed ? 0.85 : 1 },
      ]}>
      {loading ? (
        <ActivityIndicator size="small" color={foreground} />
      ) : (
        <AppText weight="bold" size={13.5} lineHeight={20} color={foreground}>
          {label}
        </AppText>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  pill: {
    minHeight: 34,
    paddingHorizontal: 14,
    paddingVertical: 7,
    borderRadius: 999,
    alignItems: 'center',
    justifyContent: 'center',
  },
  gold: {
    backgroundColor: colors.gold,
  },
  line: {
    borderWidth: 1.5,
    borderColor: colors.beige,
  },
});
