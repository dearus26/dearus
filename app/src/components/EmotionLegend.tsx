import { StyleSheet, View } from 'react-native';

import { AppText } from '@/components/AppText';
import { colors, emotionColors, emotionLabels, emotionOrder } from '@/theme/colors';

/** 캘린더 아래의 "감정별 기록 · 기쁨 설렘 …" 범례 (스토리보드 .elegend) */
export function EmotionLegend() {
  return (
    <View style={styles.row}>
      <AppText size={11} lineHeight={16} color={colors.brown}>
        감정별 기록 ·
      </AppText>
      {emotionOrder.map((emotion) => (
        <View key={emotion} style={styles.item}>
          <View style={[styles.dot, { backgroundColor: emotionColors[emotion] }]} />
          <AppText size={11} lineHeight={16} color={colors.brown}>
            {emotionLabels[emotion]}
          </AppText>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'center',
    alignItems: 'center',
    columnGap: 8,
    rowGap: 4,
  },
  item: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
  },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
  },
});
