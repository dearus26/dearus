import { Pressable, StyleSheet } from 'react-native';

import { AppText } from '@/components/AppText';
import { colors } from '@/theme/colors';

/** 커플 화면 맨 아래의 작은 글씨 버튼. 스토리보드는 설정에만 두지만, 잘못 연결했을 때 바로 풀 수 있게 여기에도 둬요. */
export function RevokeLink({ label = '동의 철회 · 연결 해제', onPress }: { label?: string; onPress: () => void }) {
  return (
    <Pressable accessibilityRole="button" accessibilityLabel={label} hitSlop={8} onPress={onPress} style={styles.link}>
      <AppText size={12.5} lineHeight={19} color={colors.muted} style={styles.text}>
        {label}
      </AppText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  link: {
    alignSelf: 'center',
    marginTop: 22,
    paddingVertical: 6,
  },
  text: {
    textDecorationLine: 'underline',
  },
});
