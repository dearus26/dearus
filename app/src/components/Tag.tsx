import { StyleSheet, View } from 'react-native';

import { AppText } from '@/components/AppText';
import { colors } from '@/theme/colors';

/** 스토리보드의 .tg예요. 필수/선택 같은 작은 알약 표시에 써요. */
export function Tag({ label, low = false }: { label: string; low?: boolean }) {
  return (
    <View style={[styles.tag, low && styles.low]}>
      <AppText weight="bold" size={11} lineHeight={16} color={colors.brown}>
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
});
