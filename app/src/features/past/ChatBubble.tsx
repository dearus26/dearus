import { Pressable, StyleSheet, View } from 'react-native';

import type { ChatMessage } from '@/api/chats';
import { AppText } from '@/components/AppText';
import { josa } from '@/features/past/labels';
import { colors } from '@/theme/colors';

type Props = {
  role: ChatMessage['role'];
  /** 답변이 아직 한 글자도 안 왔으면 undefined(점 세 개로 "입력 중"을 보여줘요) */
  text: string | undefined;
  /** 답하는 쪽 이름(스크린 리더용). 개인 모드는 "과거의 나", 커플 모드는 연인 이름이에요 */
  speaker?: string;
  /** 있으면 이 답변을 신고할 수 있어요(길게 누르기, 말풍선 아래 "신고"). 저장된 AI 답변에만 넘겨요. */
  onReport?: () => void;
};

/** 스토리보드 .m — 과거의 나(PERSONA)는 왼쪽 흰 말풍선, 나(USER)는 오른쪽 금색 말풍선이에요. */
export function ChatBubble({ role, text, speaker = '과거의 나', onReport }: Props) {
  const mine = role === 'USER';

  if (text === undefined) {
    return (
      <View
        accessible
        accessibilityLabel={`${speaker}${josa(speaker, '이', '가')} 답을 쓰는 중이에요`}
        style={[styles.bubble, styles.persona, styles.typing]}>
        <View style={[styles.dot, { opacity: 0.5 }]} />
        <View style={[styles.dot, { opacity: 0.75 }]} />
        <View style={styles.dot} />
      </View>
    );
  }

  // 저장된 AI 답변은 길게 눌러 신고할 수 있어요. 길게 누르기가 글자 선택과 겹쳐서 이 말풍선만 글자 선택을 꺼요.
  // 눈에 잘 안 띄는 제스처라서 말풍선 아래 "신고"도 함께 두고, 스크린 리더에는 "답변 신고" 동작을 줘요.
  if (!mine && onReport) {
    return (
      <View style={styles.personaGroup}>
        <Pressable
          accessible
          accessibilityLabel={`${speaker}: ${text}`}
          accessibilityActions={[{ name: 'report', label: '이 답변 신고' }]}
          onAccessibilityAction={() => onReport()}
          onLongPress={onReport}
          delayLongPress={450}
          style={[styles.bubble, styles.persona, styles.fullWidth]}>
          <AppText size={15} lineHeight={22}>
            {text}
          </AppText>
        </Pressable>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="이 답변 신고"
          hitSlop={10}
          onPress={onReport}
          style={styles.reportLink}>
          <AppText size={11.5} lineHeight={17} color={colors.muted}>
            신고
          </AppText>
        </Pressable>
      </View>
    );
  }

  return (
    <View
      accessible
      accessibilityLabel={`${mine ? '나' : speaker}: ${text}`}
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
  // 신고할 수 있는 답변: 말풍선과 그 아래 "신고"를 한 덩어리로 묶어서, 폭 제한은 이 묶음에 걸어요
  personaGroup: {
    alignSelf: 'flex-start',
    maxWidth: '78%',
    gap: 2,
  },
  fullWidth: {
    maxWidth: '100%',
  },
  reportLink: {
    alignSelf: 'flex-start',
    marginLeft: 8,
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
