import { StyleSheet, View, type ViewProps } from 'react-native';

import { colors } from '@/theme/colors';

type Props = ViewProps & {
  /** 스토리보드의 .card.tint — 살짝 진한 배경의 안내 카드 */
  tint?: boolean;
};

export function Card({ tint = false, style, ...rest }: Props) {
  return <View {...rest} style={[styles.card, tint && styles.tint, style]} />;
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: colors.paper,
    borderWidth: 1,
    borderColor: colors.beige,
    borderRadius: 20,
    padding: 16,
  },
  tint: {
    backgroundColor: colors.panel,
  },
});
