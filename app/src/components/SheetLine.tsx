import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

import { AppText } from '@/components/AppText';
import { colors } from '@/theme/colors';

/** 아래에서 올라오는 시트(BottomSheet)의 안내 한 줄이에요. 앞에 금색 ✓가 붙어요(스토리보드 .warn). */
export function SheetLine({ children }: { children: ReactNode }) {
  return (
    <View style={styles.line}>
      <AppText size={14} lineHeight={22} color={colors.gold} weight="bold">
        ✓
      </AppText>
      <AppText size={14} lineHeight={22} style={styles.text}>
        {children}
      </AppText>
    </View>
  );
}

const styles = StyleSheet.create({
  line: {
    flexDirection: 'row',
    gap: 8,
    alignItems: 'flex-start',
  },
  text: {
    flex: 1,
  },
});
