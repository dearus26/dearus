import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { AppText } from '@/components/AppText';
import { Card } from '@/components/Card';
import { colors } from '@/theme/colors';

type Props = {
  title: string;
  description: string;
  children?: ReactNode;
};

/** 아직 만들지 않은 탭 화면의 자리예요. 실제 화면을 만들면 이 컴포넌트를 걷어내요. */
export function ScreenPlaceholder({ title, description, children }: Props) {
  const insets = useSafeAreaInsets();
  return (
    <View style={[styles.screen, { paddingTop: insets.top + 16 }]}>
      <AppText serif size={22} lineHeight={30}>
        {title}
      </AppText>
      <Card tint style={styles.card}>
        <AppText size={13.5} lineHeight={21} color={colors.muted}>
          {description}
        </AppText>
      </Card>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: colors.cream,
    paddingHorizontal: 22,
  },
  card: {
    marginTop: 16,
  },
});
