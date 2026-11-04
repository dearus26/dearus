import { useRef, useState } from 'react';
import { Pressable, StyleSheet, TextInput, View, type TextInputInstance } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Icon } from '@/components/Icon';
import type { SendResult } from '@/features/past/useConversation';
import { notify } from '@/lib/dialog';
import { colors } from '@/theme/colors';
import { fontFamily } from '@/theme/fonts';

/** 한 번에 보낼 수 있는 글자 수(명세 SendMessage: 2000자) */
const MAX_LENGTH = 2000;

type Props = {
  onSend: (content: string) => Promise<SendResult>;
  /** 앞의 답변이 오는 중이거나 대화를 아직 못 불러왔을 때 */
  disabled: boolean;
};

/** 스토리보드 .inputbar — 말 거는 입력칸과 금색 보내기 버튼. 보내기에 실패하면 쓰던 글을 입력칸에 되돌려요. */
export function ChatInputBar({ onSend, disabled }: Props) {
  const insets = useSafeAreaInsets();
  const inputRef = useRef<TextInputInstance>(null); // React Native 0.88부터 ref는 컴포넌트가 아니라 인스턴스 타입을 써요
  const [text, setText] = useState('');
  const canSend = text.trim().length > 0 && !disabled;

  async function submit() {
    const content = text.trim();
    if (content.length === 0 || disabled) return;
    setText('');
    const result = await onSend(content);
    if (!result.ok) {
      // 그 사이에 새로 쓴 글이 있으면 그대로 두고, 없으면 보내려던 글을 되돌려서 다시 보낼 수 있게 해요
      setText((current) => (current.length > 0 ? current : content));
      if (result.message) notify('보내지 못했어요', result.message);
    }
  }

  return (
    <View style={[styles.bar, { paddingBottom: Math.max(insets.bottom, 10) }]}>
      {/*
        테두리 · 여백은 바깥 상자가 맡고, 입력칸 자체는 여백 없이 둬요. Android의 여러 줄 입력칸은 자기 여백과 최소 높이가
        함께 있을 때 글자 높이가 조금만 커도 칸 안에서 스크롤돼 글자가 위로 밀려 보였어요(일기 쓰기 입력칸처럼 여백 없이 두면 괜찮아요).
        여백 부분을 눌러도 입력칸에 커서가 가게 해요.
      */}
      <Pressable accessible={false} onPress={() => inputRef.current?.focus()} style={styles.field}>
        <TextInput
          ref={inputRef}
          accessibilityLabel="과거의 나에게 말 걸기"
          value={text}
          onChangeText={setText}
          placeholder="과거의 나에게 말 걸기"
          placeholderTextColor={colors.muted}
          multiline
          maxLength={MAX_LENGTH}
          textAlignVertical="top"
          style={styles.input}
        />
      </Pressable>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel="보내기"
        accessibilityState={{ disabled: !canSend }}
        disabled={!canSend}
        onPress={() => void submit()}
        style={({ pressed }) => [styles.send, { opacity: !canSend ? 0.45 : pressed ? 0.85 : 1 }]}>
        <Icon name="send" size={20} color={colors.ink} />
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 10,
    paddingTop: 10,
    paddingHorizontal: 14,
    backgroundColor: colors.paper,
    borderTopWidth: 1,
    borderTopColor: colors.beige,
  },
  field: {
    flex: 1,
    minHeight: 44,
    paddingHorizontal: 16,
    paddingVertical: 9,
    borderRadius: 22,
    borderWidth: 1.5,
    borderColor: colors.beige,
    justifyContent: 'center',
  },
  // 여러 줄을 써도 이 높이까지만 커지고, 넘으면 입력칸 안에서 스크롤돼요.
  // 줄 높이는 폰트가 원래 필요한 높이(Noto Sans KR은 글자 크기의 약 1.45~1.5배)보다 넉넉해야 해요.
  // 모자라면 Android가 첫 줄 글자를 위로 밀어 올려서 윗부분이 잘려 보여요(일기 쓰기 입력칸은 1.74배라서 괜찮아요).
  input: {
    maxHeight: 100,
    padding: 0,
    margin: 0,
    fontFamily: fontFamily.sans.regular,
    fontSize: 14.5,
    lineHeight: 25,
    includeFontPadding: false,
    color: colors.ink,
  },
  send: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: colors.gold,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
