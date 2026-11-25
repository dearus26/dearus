import { StyleSheet, View } from 'react-native';

import { AppText } from '@/components/AppText';
import { colors } from '@/theme/colors';

/** 스토리보드의 .tg예요. 필수/선택 같은 작은 알약 표시에 써요. ok는 금색으로 채운 "완료" 표시(.tg.ok)예요. */
export function Tag({ label, low = false, ok = false }: { label: string; low?: boolean; ok?: boolean }) {
  return (
    <View style={[styles.tag, low && styles.low, ok && styles.ok]}>
      <AppText weight="bold" size={11} lineHeight={16} color={ok ? colors.ink : colors.brown}>
        {label}
      </AppText>
    </View>
  );
}

const styles = StyleSheet.create({
  tag: {
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: colors.beige,
  },
  low: {
    backgroundColor: colors.panel,
    borderColor: colors.brown,
  },
  ok: {
    backgroundColor: colors.gold,
    borderColor: colors.gold,
  },
});
