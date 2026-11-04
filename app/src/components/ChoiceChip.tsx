import { Pressable, StyleSheet } from 'react-native';

import { AppText } from '@/components/AppText';
import { colors } from '@/theme/colors';

type Props = {
  label: string;
  selected: boolean;
  onPress: () => void;
  disabled?: boolean;
};

/** 스토리보드의 .chip.plain — 앞에 점이 없는 선택 칩이에요(시점 고르기의 1개월 · 6개월 · 1년 전). 하나만 고르는 용도예요. */
export function ChoiceChip({ label, selected, onPress, disabled = false }: Props) {
  return (
    <Pressable
      accessibilityRole="radio"
      accessibilityLabel={label}
      accessibilityState={{ checked: selected, disabled }}
      disabled={disabled}
      onPress={onPress}
      style={[styles.chip, selected && styles.selected, disabled && styles.disabled]}>
      <AppText size={14} lineHeight={20} weight={selected ? 'bold' : 'regular'} color={selected ? colors.ink : colors.brown}>
        {label}
      </AppText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  chip: {
    paddingVertical: 8,
    paddingHorizontal: 14,
    borderRadius: 999,
    borderWidth: 1.5,
    borderColor: colors.beige,
    backgroundColor: colors.paper,
  },
  selected: {
    backgroundColor: colors.gold,
    borderColor: colors.gold,
  },
  disabled: {
    opacity: 0.45,
  },
});
