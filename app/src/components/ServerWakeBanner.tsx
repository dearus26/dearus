import { StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { AppText } from '@/components/AppText';
import { useServerWaking } from '@/lib/serverWake';
import { colors } from '@/theme/colors';

/**
 * 요청이 오래 걸릴 때 화면 맨 위에 잠깐 보이는 안내 띠예요(2026.11.28). 무료 서버가 잠에서 깨는 동안 앱이 멈춘 게 아니라고 알려줘요.
 * 화면을 가리지 않게 누르기는 통과시켜요.
 */
export function ServerWakeBanner() {
  const insets = useSafeAreaInsets();
  const waking = useServerWaking();
  if (!waking) return null;
  return (
    <View pointerEvents="none" style={[styles.wrap, { top: insets.top + 6 }]}>
      <View style={styles.pill} accessibilityLiveRegion="polite" accessibilityRole="alert">
        <AppText size={12.5} lineHeight={18} color={colors.cream} weight="bold">
          서버를 깨우는 중이에요. 처음엔 10초쯤 걸릴 수 있어요
        </AppText>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    position: 'absolute',
    left: 0,
    right: 0,
    alignItems: 'center',
    zIndex: 100,
  },
  pill: {
    paddingHorizontal: 14,
    paddingVertical: 7,
    borderRadius: 999,
    backgroundColor: colors.brown,
  },
});
