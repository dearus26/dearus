import { StyleSheet, View } from 'react-native';

import { Icon } from '@/components/Icon';
import { colors } from '@/theme/colors';

/** 스토리보드의 .box예요. 누르는 동작은 바깥 Pressable(한 줄 전체)이 맡아요. */
export function CheckBox({ checked }: { checked: boolean }) {
  return (
    <View style={[styles.box, checked && styles.boxOn]}>
      {checked ? <Icon name="check" size={16} color={colors.ink} /> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  box: {
    width: 22,
    height: 22,
    borderRadius: 7,
    borderWidth: 2,
    borderColor: colors.beige,
    backgroundColor: colors.paper,
    alignItems: 'center',
    justifyContent: 'center',
  },
  boxOn: {
    backgroundColor: colors.gold,
    borderColor: colors.gold,
  },
});
