import { ScrollView, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import type { CalendarDay, Diary } from '@/api/diaries';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { formatKoreanDate, isPickablePastDate } from '@/lib/date';
import { colors, emotionColors, emotionLabels, type Emotion } from '@/theme/colors';

type Props = {
  /** 고른 날짜 */
  date: string;
  /** 그날의 일기(없으면 undefined) */
  diary: Diary | undefined;
  /** 홈 캘린더의 그날(GET /calendar). 메신저를 올린 날이면 AI 추천 감정 · 한 줄 요약이 있어요(2026.11.28) */
  calendarDay?: CalendarDay;
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
  /** "메신저 올리기"(그날이 골라진 채로 메신저 올리기를 열어요, 2026.11.28) */
  onUpload: (date: string) => void;
  style?: StyleProp<ViewStyle>;
};

/**
 * 홈 캘린더 아래에 늘 붙어 있는 날짜 패널이에요(처음에는 팝업 시트였어요).
 * 고른 날의 일기를 보여주고, 내용이 길면 이 안에서만 스크롤돼요. 버튼은 한 줄에 나란히 둬서 작게 했어요.
 * 스토리보드에는 날짜를 누르면 그날의 나와 대화한다고만 있어서, 일기 보기 · 고치기 · 지우기를 함께 두었어요(가정).
 * "이 날의 나와 대화"는 일기가 없는 날에도 보여요(일기 대신 메신저를 올린 날, 2026.11.28). 1년보다 오래된 날은 고를 수 없어서 숨겨요.
 * 메신저를 올린 날은 AI가 만든 한 줄 요약과 추천 감정을 보여줘요. 추천 감정은 직접 고른 감정과 다르게 "AI 추천"으로 표시해요(2026.11.28).
 */
export function DayPanel({ date, diary, calendarDay, loading, failed, deleting, onRetry, onWrite, onDelete, onChat, onUpload, style }: Props) {
  const canChat = isPickablePastDate(date);
  const messenger = calendarDay?.hasMessenger ? calendarDay : undefined;
  // 일기에 직접 고른 감정이 먼저예요. 일기가 없는 날만 AI 추천 감정을 머리에 보여줘요
  const suggested = !diary && messenger ? messenger.suggestedEmotions : [];
  return (
    <Card style={[styles.panel, style]}>
      <View style={styles.head}>
        <AppText serif weight="bold" size={17} lineHeight={24}>
          {formatKoreanDate(date)}
        </AppText>
        {diary?.emotions.map((emotion) => <EmotionTag key={emotion} emotion={emotion} />)}
        {suggested.map((emotion) => <EmotionTag key={emotion} emotion={emotion} suggested />)}
        {suggested.length > 0 ? (
          <AppText size={11} lineHeight={16} color={colors.muted}>
            AI 추천
          </AppText>
        ) : null}
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
          <>
            <AppText size={14.5} lineHeight={23}>
              {diary.content}
            </AppText>
            {messenger ? (
              <AppText size={12} lineHeight={18} color={colors.brown} style={styles.emptyHint}>
                {messenger.messengerLine ? `이 날 올린 메신저 대화: ${messenger.messengerLine}` : '이 날은 메신저 대화도 올렸어요'}
              </AppText>
            ) : null}
          </>
        ) : messenger ? (
          <>
            <AppText size={13} lineHeight={20} color={colors.brown}>
              이 날은 메신저 대화를 올렸어요
            </AppText>
            {messenger.messengerLine ? (
              <AppText size={14.5} lineHeight={23} style={styles.emptyHint}>
                {messenger.messengerLine}
              </AppText>
            ) : null}
            <AppText size={12} lineHeight={18} color={colors.muted} style={styles.emptyHint}>
              {messenger.suggestedEmotions.length > 0
                ? 'AI가 대화를 보고 추천한 감정이에요. 일기를 쓰면 직접 고른 감정이 먼저 보여요'
                : '일기를 쓰면 그날의 감정을 직접 남길 수 있어요'}
            </AppText>
          </>
        ) : (
          <>
            <AppText size={14} lineHeight={22} color={colors.muted}>
              이 날은 쓴 일기가 없어요.
            </AppText>
            <AppText size={12} lineHeight={18} color={colors.brown} style={styles.emptyHint}>
              {canChat
                ? '일기를 쓰거나 이 날의 메신저 대화를 올리면 그날의 나와 대화할 수 있어요'
                : '1년보다 오래된 날은 그날의 나와 대화할 수 없어요'}
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
            {canChat ? (
              <View style={styles.chatButton}>
                <Button size="small" label="이 날의 나와 대화" onPress={() => onChat(date)} />
              </View>
            ) : null}
            <View style={styles.sideButton}>
              <Button size="small" label="수정" variant="line" onPress={() => onWrite(date)} />
            </View>
            <View style={styles.sideButton}>
              <Button size="small" label="지우기" variant="line" loading={deleting} onPress={() => onDelete(diary)} />
            </View>
          </>
        ) : (
          <>
            <View style={styles.fullButton}>
              <Button size="small" label="일기 쓰기" disabled={loading} onPress={() => onWrite(date)} />
            </View>
            {/* 메신저만 올린 날은 그 기록으로 만나고, 기록이 없는 날은 바로 메신저를 올릴 수 있어요(2026.11.28) */}
            {canChat && messenger ? (
              <View style={styles.fullButton}>
                <Button size="small" label="이 날의 나와 대화" variant="line" disabled={loading} onPress={() => onChat(date)} />
              </View>
            ) : canChat ? (
              <View style={styles.fullButton}>
                <Button size="small" label="메신저 올리기" variant="line" disabled={loading} onPress={() => onUpload(date)} />
              </View>
            ) : null}
          </>
        )}
      </View>
    </Card>
  );
}

/** 감정 태그예요. suggested면 AI 추천이라 테두리를 점선으로 하고 점을 비워요(직접 고른 감정과 구분돼요). */
function EmotionTag({ emotion, suggested = false }: { emotion: Emotion; suggested?: boolean }) {
  return (
    <View style={[styles.tag, suggested && styles.tagSuggested]}>
      <View
        style={[
          styles.tagDot,
          suggested ? { borderWidth: 1.5, borderColor: emotionColors[emotion] } : { backgroundColor: emotionColors[emotion] },
        ]}
      />
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
  tagSuggested: {
    borderStyle: 'dashed',
    backgroundColor: 'transparent',
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
