import { StyleSheet, View } from 'react-native';

import { AppText } from '@/components/AppText';
import { Tag } from '@/components/Tag';
import { colors } from '@/theme/colors';

type Side = {
  name: string;
  /** 이름 아래 작은 표시: "동의 완료" · "동의 전" · "대기 중" */
  tag: string;
  tone: 'ok' | 'low' | 'plain';
};

type Props = {
  me: Side;
  partner: Side;
  /** 두 사람이 모두 동의해서 이어졌으면 true(실선), 아니면 점선이에요 */
  linked: boolean;
};

/** 스토리보드 .pair — 나와 연인의 동그라미 둘과 그 사이의 선. 나는 금색, 연인은 갈색이에요. */
export function PairHeader({ me, partner, linked }: Props) {
  return (
    <View style={styles.pair}>
      <Person side={me} />
      <View style={[styles.link, linked && styles.linkOn]} />
      <Person side={partner} alt />
    </View>
  );
}

function Person({ side, alt = false }: { side: Side; alt?: boolean }) {
  return (
    <View
      accessible
      accessibilityLabel={`${alt ? side.name : '나'}, ${side.tag}`}
      style={styles.person}>
      <View style={[styles.avatar, alt && styles.avatarAlt]}>
        <AppText serif weight="bold" size={20} lineHeight={28} color={alt ? colors.cream : colors.ink}>
          {side.name.slice(0, 1)}
        </AppText>
      </View>
      <AppText size={14} lineHeight={20}>
        {alt ? side.name : '나'}
      </AppText>
      <Tag label={side.tag} ok={side.tone === 'ok'} low={side.tone === 'low'} />
    </View>
  );
}

const styles = StyleSheet.create({
  pair: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'center',
    gap: 14,
    marginTop: 6,
  },
  person: {
    alignItems: 'center',
    gap: 3,
  },
  avatar: {
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: colors.gold,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarAlt: {
    backgroundColor: colors.brown,
  },
  // 동그라미 가운데 높이에 오도록 위에서 28만큼 내려요
  link: {
    width: 54,
    marginTop: 28,
    borderTopWidth: 2,
    borderStyle: 'dashed',
    borderColor: colors.beige,
  },
  linkOn: {
    borderTopWidth: 3,
    borderStyle: 'solid',
    borderColor: colors.gold,
  },
});
