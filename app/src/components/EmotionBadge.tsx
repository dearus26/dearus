import { StyleSheet, View } from 'react-native';

import { AppText } from '@/components/AppText';
import { colors, emotionColors, emotionLabels, type Emotion } from '@/theme/colors';

/** 스토리보드의 .tg(감정 이름표)를 감정 색으로 채운 모양이에요. 목록과 시점 고르기 카드에서 그날의 감정을 알려줘요. */
export function EmotionBadge({ emotion }: { emotion: Emotion }) {
  const color = emotionColors[emotion];
  return (
    <View style={[styles.badge, { backgroundColor: color, borderColor: color }]}>
      <AppText weight="bold" size={11.5} lineHeight={16} color={colors.white}>
        {emotionLabels[emotion]}
      </AppText>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    paddingHorizontal: 8,
    paddingVertical: 1,
    borderRadius: 999,
    borderWidth: 1,
  },
});
