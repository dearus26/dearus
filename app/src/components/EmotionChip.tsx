import { Pressable, StyleSheet, View } from 'react-native';

import { AppText } from '@/components/AppText';
import { colors, emotionColors, emotionLabels, type Emotion } from '@/theme/colors';

type Props = {
  emotion: Emotion;
  selected: boolean;
  onPress: () => void;
};

/** 스토리보드의 .chip — 감정 색 점이 앞에 있고, 고르면 그 감정 색으로 채워져요. */
export function EmotionChip({ emotion, selected, onPress }: Props) {
  const color = emotionColors[emotion];
  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityLabel={emotionLabels[emotion]}
      accessibilityState={{ checked: selected }}
      onPress={onPress}
      style={[styles.chip, selected && { backgroundColor: color, borderColor: color }]}>
      <View style={[styles.dot, { backgroundColor: selected ? colors.white : color }]} />
      <AppText
        size={14}
        lineHeight={20}
        weight={selected ? 'bold' : 'regular'}
        color={selected ? colors.white : colors.brown}>
        {emotionLabels[emotion]}
      </AppText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 7,
    paddingVertical: 8,
    paddingLeft: 12,
    paddingRight: 14,
    borderRadius: 999,
    borderWidth: 1.5,
    borderColor: colors.beige,
    backgroundColor: colors.paper,
  },
  dot: {
    width: 7,
    height: 7,
    borderRadius: 3.5,
  },
});
