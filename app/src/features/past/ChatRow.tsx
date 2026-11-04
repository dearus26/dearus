import { Pressable, StyleSheet, View } from 'react-native';

import type { ChatListItem } from '@/api/chats';
import { AppText } from '@/components/AppText';
import { EmotionBadge } from '@/components/EmotionBadge';
import { personaBadge, personaTitle } from '@/features/past/labels';
import { formatRelative } from '@/lib/date';
import { colors, emotionColors } from '@/theme/colors';

type Props = {
  chat: ChatListItem;
  onPress: () => void;
};

/**
 * 스토리보드 B-1의 목록 한 줄: 동그라미(바로가기는 "1개월", 직접 고른 날은 "10/4") + 제목 · 감정 태그 + 마지막 메시지 한 줄 + 언제.
 * 불러오는 중이거나 실패한 세션은 마지막 메시지 자리에 상태를 알려줘요.
 */
export function ChatRow({ chat, onPress }: Props) {
  const title = personaTitle(chat);
  const emotion = chat.emotions?.[0];
  // 동그라미 색: 직접 고른 날은 그날 감정 색, 바로가기는 금색(1년은 갈색, 스토리보드 .av.alt)
  const background = emotion
    ? emotionColors[emotion]
    : chat.shortcutKey === 'ONE_YEAR'
      ? colors.brown
      : colors.gold;
  const badgeColor = !emotion && chat.shortcutKey === 'ONE_YEAR' ? colors.cream : colors.ink;

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={title}
      onPress={onPress}
      style={({ pressed }) => [styles.row, pressed && styles.pressed]}>
      <View style={[styles.badge, { backgroundColor: background }]}>
        <AppText serif weight="bold" size={chat.shortcutKey ? 14 : 13} lineHeight={18} color={badgeColor}>
          {personaBadge(chat)}
        </AppText>
      </View>

      <View style={styles.main}>
        <View style={styles.titleRow}>
          <AppText weight="bold" size={15.5} lineHeight={22} numberOfLines={1} style={styles.title}>
            {title}
          </AppText>
          {emotion ? <EmotionBadge emotion={emotion} /> : null}
        </View>
        <AppText size={12.5} lineHeight={19} color={colors.brown} numberOfLines={1} style={styles.preview}>
          {previewText(chat)}
        </AppText>
      </View>

      <AppText size={12.5} lineHeight={19} color={colors.brown} numberOfLines={1}>
        {formatRelative(chat.lastMessageAt)}
      </AppText>
    </Pressable>
  );
}

function previewText(chat: ChatListItem): string {
  if (chat.status === 'PREPARING') return '그때의 나를 불러오는 중이에요';
  if (chat.status === 'FAILED') return '불러오지 못했어요. 눌러서 다시 시도해요';
  return chat.lastMessagePreview ?? '대화를 시작해 보세요';
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
    paddingVertical: 13,
    paddingHorizontal: 2,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(205, 182, 156, 0.6)',
  },
  pressed: {
    opacity: 0.6,
  },
  badge: {
    width: 48,
    height: 48,
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
  },
  main: {
    flex: 1,
    minWidth: 0,
  },
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
  },
  // 제목이 길어도 감정 태그가 밀려나지 않게 제목만 줄어들어요
  title: {
    flexShrink: 1,
  },
  preview: {
    marginTop: 2,
  },
});
