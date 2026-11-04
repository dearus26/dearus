import { StyleSheet, View } from 'react-native';

import type { ChatMessage } from '@/api/chats';
import { AppText } from '@/components/AppText';
import { colors } from '@/theme/colors';

type Props = {
  role: ChatMessage['role'];
  /** 답변이 아직 한 글자도 안 왔으면 undefined(점 세 개로 "입력 중"을 보여줘요) */
  text: string | undefined;
};

/** 스토리보드 .m — 과거의 나(PERSONA)는 왼쪽 흰 말풍선, 나(USER)는 오른쪽 금색 말풍선이에요. */
export function ChatBubble({ role, text }: Props) {
  const mine = role === 'USER';

  if (text === undefined) {
    return (
      <View
        accessible
        accessibilityLabel="과거의 나가 답을 쓰는 중이에요"
        style={[styles.bubble, styles.persona, styles.typing]}>
        <View style={[styles.dot, { opacity: 0.5 }]} />
        <View style={[styles.dot, { opacity: 0.75 }]} />
        <View style={styles.dot} />
      </View>
    );
  }

  return (
    <View
      accessible
      accessibilityLabel={`${mine ? '나' : '과거의 나'}: ${text}`}
      style={[styles.bubble, mine ? styles.mine : styles.persona]}>
      <AppText size={15} lineHeight={22} selectable>
        {text}
      </AppText>
    </View>
  );
}

const styles = StyleSheet.create({
  bubble: {
    maxWidth: '78%',
    paddingVertical: 11,
    paddingHorizontal: 14,
    borderRadius: 20,
  },
  persona: {
    alignSelf: 'flex-start',
    backgroundColor: colors.paper,
    borderWidth: 1,
    borderColor: colors.beige,
    borderBottomLeftRadius: 6,
  },
  mine: {
    alignSelf: 'flex-end',
    backgroundColor: colors.gold,
    borderBottomRightRadius: 6,
  },
  typing: {
    flexDirection: 'row',
    gap: 5,
    paddingVertical: 14,
    paddingHorizontal: 16,
  },
  dot: {
    width: 7,
    height: 7,
    borderRadius: 3.5,
    backgroundColor: colors.brown,
  },
});
