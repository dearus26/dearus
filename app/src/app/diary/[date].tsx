import { useQueryClient } from '@tanstack/react-query';
import { router, useLocalSearchParams } from 'expo-router';
import { useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  TextInput,
  View,
} from 'react-native';
import Svg, { Path } from 'react-native-svg';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ApiError, errorMessage } from '@/api/errors';
import { AppText } from '@/components/AppText';
import { Card } from '@/components/Card';
import { EmotionChip } from '@/components/EmotionChip';
import { Icon } from '@/components/Icon';
import { Pill } from '@/components/Pill';
import { diaryKeys, useMonthDiaries, useSaveDiary } from '@/features/diary/hooks';
import { formatKoreanDate, isFuture, isValidDateString, parseDateString, todayString } from '@/lib/date';
import { confirm, notify } from '@/lib/dialog';
import { colors, emotionOrder, type Emotion } from '@/theme/colors';
import { fontFamily } from '@/theme/fonts';

const MAX_LENGTH = 5000;

/** 스토리보드 A-4 일기 쓰기 (이미 쓴 날이면 같은 화면에서 고쳐요) */
export default function DiaryScreen() {
  const insets = useSafeAreaInsets();
  const queryClient = useQueryClient();
  const { date: rawDate } = useLocalSearchParams<{ date: string }>();

  const today = todayString();
  const validDate = isValidDateString(rawDate) && !isFuture(rawDate, today);
  // 훅은 조건 없이 불러야 해서, 잘못된 날짜면 오늘로 대신 두고 아래에서 안내 화면을 보여줘요
  const date = validDate ? rawDate : today;
  const { year, month } = parseDateString(date);

  const monthDiaries = useMonthDiaries(year, month);
  const existing = monthDiaries.data?.find((diary) => diary.date === date);
  const save = useSaveDiary();

  // 사용자가 아직 건드리지 않은 값(null)은 서버에 있는 일기를 그대로 보여줘요
  const [draftContent, setDraftContent] = useState<string | null>(null);
  const [draftEmotions, setDraftEmotions] = useState<Emotion[] | null>(null);
  const [tipHidden, setTipHidden] = useState(false);

  const content = draftContent ?? existing?.content ?? '';
  const emotions = draftEmotions ?? existing?.emotions ?? [];

  const contentChanged = draftContent !== null && draftContent !== (existing?.content ?? '');
  const emotionsChanged = draftEmotions !== null && !sameEmotions(draftEmotions, existing?.emotions ?? []);
  const dirty = contentChanged || emotionsChanged;
  const canSave = content.trim().length > 0 && dirty && !monthDiaries.isLoading;

  const isToday = date === today;

  function leave() {
    if (router.canGoBack()) router.back();
    else router.replace('/');
  }

  async function goBack() {
    if (dirty) {
      const ok = await confirm({
        title: '작성 중인 내용이 사라져요',
        message: '저장하지 않고 나갈까요?',
        confirmLabel: '나가기',
        destructive: true,
      });
      if (!ok) return;
    }
    leave();
  }

  function toggleEmotion(emotion: Emotion) {
    const next = emotions.includes(emotion) ? emotions.filter((e) => e !== emotion) : [...emotions, emotion];
    setDraftEmotions(emotionOrder.filter((e) => next.includes(e)));
  }

  async function submit() {
    try {
      await save.mutateAsync({ id: existing?.id, date, content: content.trim(), emotions });
      leave();
    } catch (error) {
      if (error instanceof ApiError && error.code === 'DIARY_ALREADY_EXISTS') {
        // 다른 기기에서 먼저 쓴 경우예요. 목록을 새로 받아서 고치기 모드로 열리게 해요.
        await queryClient.invalidateQueries({ queryKey: diaryKeys.all });
        notify('이미 쓴 일기가 있어요', '그날의 일기를 불러왔어요. 확인하고 다시 저장해 주세요.');
        return;
      }
      notify('일기를 저장하지 못했어요', errorMessage(error));
    }
  }

  if (!validDate) {
    return (
      <View style={[styles.screen, { paddingTop: insets.top }]}>
        <View style={styles.navbar}>
          <Pressable accessibilityRole="button" accessibilityLabel="뒤로 가기" hitSlop={12} onPress={leave}>
            <Icon name="chevronLeft" size={22} color={colors.brown} />
          </Pressable>
          <AppText weight="bold" size={17}>
            일기 쓰기
          </AppText>
          <View style={styles.navSide} />
        </View>
        <AppText size={14} lineHeight={22} color={colors.muted} style={styles.invalid}>
          열 수 없는 날짜예요. 오늘이나 지난 날짜만 쓸 수 있어요.
        </AppText>
      </View>
    );
  }

  return (
    <KeyboardAvoidingView
      style={styles.screen}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <View style={{ paddingTop: insets.top }}>
        <View style={styles.navbar}>
          <Pressable accessibilityRole="button" accessibilityLabel="뒤로 가기" hitSlop={12} onPress={goBack}>
            <Icon name="chevronLeft" size={22} color={colors.brown} />
          </Pressable>
          <AppText weight="bold" size={17}>
            {existing ? '일기 고치기' : '일기 쓰기'}
          </AppText>
          <View style={styles.navSide}>
            <Pill label="저장" onPress={() => void submit()} disabled={!canSave} loading={save.isPending} />
          </View>
        </View>
      </View>

      <ScrollView
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={[styles.content, { paddingBottom: Math.max(insets.bottom, 12) + 24 }]}
        showsVerticalScrollIndicator={false}>
        <AppText serif weight="bold" size={19} lineHeight={26} style={styles.dateTitle}>
          {formatKoreanDate(date)}
        </AppText>

        <AppText size={12.5} lineHeight={19} color={colors.brown} style={styles.moodLabel}>
          {`${isToday ? '오늘' : '이날'}의 기분 (여러 개 고를 수 있어요)`}
        </AppText>
        <View style={styles.chips}>
          {emotionOrder.map((emotion) => (
            <EmotionChip
              key={emotion}
              emotion={emotion}
              selected={emotions.includes(emotion)}
              onPress={() => toggleEmotion(emotion)}
            />
          ))}
        </View>

        {tipHidden ? null : (
          <Card tint style={styles.tip}>
            <Svg width={20} height={20} viewBox="0 0 24 24" fill="none" style={styles.tipIcon}>
              <Path
                d="M9 18h6M10 21h4M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.2 1 2.1h5c0-.9.4-1.6 1-2.1A6 6 0 0 0 12 3z"
                stroke={colors.gold}
                strokeWidth={2}
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </Svg>
            <View style={styles.tipBody}>
              <AppText weight="bold" size={13.5} lineHeight={20}>
                말하듯이 써 보세요
              </AppText>
              <AppText size={13} lineHeight={20}>
                친구에게 말하는 말투로 쓰면, 과거의 나와 대화할 때 더 나다운 말투가 나와요.
              </AppText>
              <AppText size={12} lineHeight={18} color={colors.brown} style={styles.tipExample}>
                “피곤하다.” → “아 오늘 진짜 피곤해”
              </AppText>
            </View>
            <Pressable accessibilityRole="button" accessibilityLabel="안내 닫기" hitSlop={10} onPress={() => setTipHidden(true)}>
              <AppText size={12.5} lineHeight={19} color={colors.brown}>
                닫기
              </AppText>
            </Pressable>
          </Card>
        )}

        <Card style={styles.editor}>
          <TextInput
            accessibilityLabel="일기 내용"
            value={content}
            onChangeText={setDraftContent}
            multiline
            maxLength={MAX_LENGTH}
            placeholder="오늘 하루는 어땠어? 친구에게 말하듯이 편하게 써 봐"
            placeholderTextColor={colors.muted}
            textAlignVertical="top"
            style={styles.input}
          />
          <AppText size={12.5} lineHeight={19} color={colors.brown} style={styles.counter}>
            {`${content.length}자`}
          </AppText>
        </Card>

        <Card tint style={styles.messengerCard}>
          <View style={styles.messengerText}>
            <AppText weight="bold" size={14.5} lineHeight={21}>
              쓰기 귀찮은 날엔?
            </AppText>
            <AppText size={12.5} lineHeight={19} color={colors.brown}>
              메신저 대화를 올리면 대신 분석해 둘게요
            </AppText>
          </View>
          <Pill
            label="메신저 올리기"
            tone="line"
            // 메신저 올리기(글자 읽기 · 가리기)는 다음 단계에서 만들어요
            onPress={() => notify('곧 열려요', '메신저 대화 올리기는 다음 단계에서 만들어요.')}
          />
        </Card>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

function sameEmotions(a: Emotion[], b: Emotion[]): boolean {
  return a.length === b.length && a.every((emotion) => b.includes(emotion));
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: colors.cream,
  },
  navbar: {
    height: 48,
    paddingHorizontal: 22,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  navSide: {
    minWidth: 52,
    alignItems: 'flex-end',
  },
  invalid: {
    paddingHorizontal: 22,
    marginTop: 24,
  },
  content: {
    paddingHorizontal: 22,
    paddingTop: 8,
  },
  dateTitle: {
    marginTop: 2,
  },
  moodLabel: {
    marginTop: 12,
    marginBottom: 8,
  },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  tip: {
    marginTop: 12,
    paddingVertical: 12,
    paddingHorizontal: 14,
    flexDirection: 'row',
    gap: 10,
    alignItems: 'flex-start',
  },
  tipIcon: {
    marginTop: 1,
  },
  tipBody: {
    flex: 1,
  },
  tipExample: {
    marginTop: 5,
  },
  editor: {
    marginTop: 14,
    minHeight: 260,
  },
  input: {
    minHeight: 200,
    padding: 0,
    fontFamily: fontFamily.sans.regular,
    fontSize: 15.5,
    lineHeight: 27,
    color: colors.ink,
  },
  counter: {
    alignSelf: 'flex-end',
    marginTop: 8,
  },
  messengerCard: {
    marginTop: 14,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  messengerText: {
    flex: 1,
  },
});
