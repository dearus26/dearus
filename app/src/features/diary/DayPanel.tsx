import { ScrollView, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import type { Diary } from '@/api/diaries';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { formatKoreanDate } from '@/lib/date';
import { colors, emotionColors, emotionLabels, type Emotion } from '@/theme/colors';

type Props = {
  /** 고른 날짜 */
  date: string;
  /** 그날의 일기(없으면 undefined) */
  diary: Diary | undefined;
  /** 그날이 속한 달의 일기를 아직 받는 중이에요. */
  loading: boolean;
  /** 일기를 불러오지 못했어요. */
  failed: boolean;
  deleting: boolean;
  onRetry: () => void;
  /** 새로 쓰기 · 고치기 (같은 화면으로 가요) */
  onWrite: (date: string) => void;
  onDelete: (diary: Diary) => void;
  /** "이 날의 나와 대화" */
  onChat: (date: string) => void;
  style?: StyleProp<ViewStyle>;
};

/**
 * 홈 캘린더 아래에 늘 붙어 있는 날짜 패널이에요(처음에는 팝업 시트였어요).
 * 고른 날의 일기를 보여주고, 내용이 길면 이 안에서만 스크롤돼요. 버튼은 한 줄에 나란히 둬서 작게 했어요.
 * 스토리보드에는 날짜를 누르면 그날의 나와 대화한다고만 있어서, 일기 보기 · 고치기 · 지우기를 함께 두었어요(가정).
 */
export function DayPanel({ date, diary, loading, failed, deleting, onRetry, onWrite, onDelete, onChat, style }: Props) {
  return (
    <Card style={[styles.panel, style]}>
      <View style={styles.head}>
        <AppText serif weight="bold" size={17} lineHeight={24}>
          {formatKoreanDate(date)}
        </AppText>
        {diary?.emotions.map((emotion) => <EmotionTag key={emotion} emotion={emotion} />)}
      </View>

      <ScrollView style={styles.body} showsVerticalScrollIndicator>
        {loading ? (
          <AppText size={14} lineHeight={22} color={colors.muted}>
            불러오는 중이에요…
          </AppText>
        ) : failed ? (
          <AppText size={14} lineHeight={22} color={colors.muted}>
            일기를 불러오지 못했어요.
          </AppText>
        ) : diary ? (
          <AppText size={14.5} lineHeight={23}>
            {diary.content}
          </AppText>
        ) : (
          <>
            <AppText size={14} lineHeight={22} color={colors.muted}>
              이 날은 쓴 일기가 없어요.
            </AppText>
            <AppText size={12} lineHeight={18} color={colors.brown} style={styles.emptyHint}>
              일기가 있는 날만 그날의 나와 대화할 수 있어요
            </AppText>
          </>
        )}
      </ScrollView>

      <View style={styles.actions}>
        {failed ? (
          <View style={styles.fullButton}>
            <Button size="small" label="다시 불러오기" variant="line" onPress={onRetry} />
          </View>
        ) : diary ? (
          <>
            <View style={styles.chatButton}>
              <Button size="small" label="이 날의 나와 대화" onPress={() => onChat(date)} />
            </View>
            <View style={styles.sideButton}>
              <Button size="small" label="수정" variant="line" onPress={() => onWrite(date)} />
            </View>
            <View style={styles.sideButton}>
              <Button size="small" label="지우기" variant="line" loading={deleting} onPress={() => onDelete(diary)} />
            </View>
          </>
        ) : (
          <View style={styles.fullButton}>
            <Button size="small" label="일기 쓰기" disabled={loading} onPress={() => onWrite(date)} />
          </View>
        )}
      </View>
    </Card>
  );
}

function EmotionTag({ emotion }: { emotion: Emotion }) {
  return (
    <View style={styles.tag}>
      <View style={[styles.tagDot, { backgroundColor: emotionColors[emotion] }]} />
      <AppText size={11.5} lineHeight={16} weight="bold" color={colors.brown}>
        {emotionLabels[emotion]}
      </AppText>
    </View>
  );
}

const styles = StyleSheet.create({
  panel: {
    padding: 14,
  },
  head: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    columnGap: 8,
    rowGap: 4,
  },
  tag: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    paddingHorizontal: 9,
    paddingVertical: 2,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: colors.beige,
    backgroundColor: colors.cream,
  },
  tagDot: {
    width: 7,
    height: 7,
    borderRadius: 3.5,
  },
  body: {
    flex: 1,
    marginTop: 10,
  },
  emptyHint: {
    marginTop: 6,
  },
  actions: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 10,
  },
  // 한 줄 배치(row) 안에서는 버튼이 저절로 늘어나지 않아서, 감싸는 칸이 폭을 채워요
  fullButton: {
    flex: 1,
  },
  chatButton: {
    flex: 2.4,
  },
  sideButton: {
    flex: 1,
  },
});
