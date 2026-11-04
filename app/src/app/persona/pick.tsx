import type { UseQueryResult } from '@tanstack/react-query';
import { router } from 'expo-router';
import { useEffect, useRef, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View, useWindowDimensions } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import type { ChatCreateInput } from '@/api/chats';
import { errorMessage } from '@/api/errors';
import type { PersonaWindow, ShortcutKey } from '@/api/personas';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { ChoiceChip } from '@/components/ChoiceChip';
import { EmotionBadge } from '@/components/EmotionBadge';
import { EmotionLegend } from '@/components/EmotionLegend';
import { Icon } from '@/components/Icon';
import { MonthCalendar } from '@/components/MonthCalendar';
import { useMonthDiaries } from '@/features/diary/hooks';
import { useCreateChat, usePersonaPreview, useShortcuts } from '@/features/past/hooks';
import { levelNote, personaTitle, shortcutLabels, shortcutOrder, windowText } from '@/features/past/labels';
import { addDays, addMonths, parseDateString, todayString } from '@/lib/date';
import { notify } from '@/lib/dialog';
import { colors, type Emotion } from '@/theme/colors';

/** 오늘로부터 이만큼 전까지 고를 수 있어요(명세 anchorDate: 365일 전 ~ 오늘) */
const MAX_DAYS_AGO = 365;

/** 바로가기 칩 하나, 아니면 캘린더에서 고른 하루. 둘 중 하나만 골라져 있어요. */
type Choice = { kind: 'shortcut'; key: ShortcutKey } | { kind: 'day'; date: string };

/**
 * 스토리보드 B-2 시점 고르기. 1개월 · 6개월 · 1년 전 바로가기(전후 2주)를 고르거나,
 * 캘린더에서 날짜를 직접 골라요(그날 하루). 고르면 그 무렵 기록이 얼마나 있는지 보여주고 "만나러 가기"로 대화를 시작해요.
 */
export default function PickScreen() {
  const insets = useSafeAreaInsets();
  // 위쪽(제목 · 칩)과 아래쪽(요약 카드 · 버튼)이 길어서, 낮은 폰에서는 달력 줄을 줄여요. 화면은 스크롤돼요.
  const { height: windowHeight } = useWindowDimensions();
  const calendarSize = windowHeight >= 700 ? 'compact' : 'tight';

  const today = todayString();
  const earliest = addDays(today, -MAX_DAYS_AGO);
  const thisMonth = monthIndex(parseDateString(today));
  const firstMonth = monthIndex(parseDateString(earliest));

  const [view, setView] = useState(() => {
    const { year, month } = parseDateString(today);
    return { year, month };
  });
  const [choice, setChoice] = useState<Choice | null>(null);

  const shortcuts = useShortcuts();
  const monthDiaries = useMonthDiaries(view.year, view.month);
  const dayPreview = usePersonaPreview(choice?.kind === 'day' ? choice.date : null, 'DAY');
  const createChat = useCreateChat();

  const markers: Record<string, Emotion[]> = {};
  for (const diary of monthDiaries.data ?? []) markers[diary.date] = diary.emotions;

  const selectedShortcut =
    choice?.kind === 'shortcut' ? shortcuts.data?.find((item) => item.key === choice.key) : undefined;

  // 기록량을 받아 온 뒤에만 만나러 갈 수 있어요(받기 전에는 어떤 시점인지 확정되지 않았어요)
  const chatInput: ChatCreateInput | null =
    choice?.kind === 'shortcut' && selectedShortcut
      ? {
          anchorDate: selectedShortcut.anchorDate,
          scope: selectedShortcut.scope,
          shortcutKey: selectedShortcut.key,
          forCouple: false,
        }
      : choice?.kind === 'day' && dayPreview.data
        ? { anchorDate: dayPreview.data.anchorDate, scope: 'DAY', forCouple: false }
        : null;

  // 낮은 폰에서는 아래의 요약 카드가 버튼 뒤로 가려질 수 있어서, 고른 시점의 기록량이 오면 카드가 보이게 맨 아래로 내려요
  const scrollRef = useRef<ScrollView>(null);
  const summaryKey = chatInput ? `${chatInput.scope}|${chatInput.anchorDate}` : null;
  useEffect(() => {
    if (summaryKey === null) return;
    const timer = setTimeout(() => scrollRef.current?.scrollToEnd({ animated: true }), 80);
    return () => clearTimeout(timer);
  }, [summaryKey]);

  function leave() {
    if (router.canGoBack()) router.back();
    else router.replace('/past');
  }

  async function meet() {
    if (!chatInput) return;
    try {
      const session = await createChat.mutateAsync(chatInput);
      // 뒤로 가면 시점 고르기가 아니라 목록으로 돌아오게 replace로 열어요
      router.replace({ pathname: '/chat/[id]', params: { id: session.id } });
    } catch (error) {
      notify('과거의 나를 만나지 못했어요', errorMessage(error));
    }
  }

  return (
    <View style={[styles.screen, { paddingTop: insets.top }]}>
      <View style={styles.navbar}>
        <Pressable accessibilityRole="button" accessibilityLabel="뒤로 가기" hitSlop={12} onPress={leave}>
          <Icon name="chevronLeft" size={22} color={colors.brown} />
        </Pressable>
        <AppText weight="bold" size={17}>
          과거의 나 만나기
        </AppText>
        <View style={styles.navSide} />
      </View>

      <ScrollView ref={scrollRef} contentContainerStyle={styles.content} showsVerticalScrollIndicator={false}>
        <AppText serif weight="bold" size={24} lineHeight={34} style={styles.title}>
          {'언제의 나를\n만나볼까요?'}
        </AppText>

        <View style={styles.chips} accessibilityRole="radiogroup">
          {shortcutOrder.map((key) => (
            <ChoiceChip
              key={key}
              label={shortcutLabels[key].chip}
              selected={choice?.kind === 'shortcut' && choice.key === key}
              disabled={!shortcuts.data}
              onPress={() => setChoice({ kind: 'shortcut', key })}
            />
          ))}
        </View>
        {shortcuts.isError ? (
          <Pressable accessibilityRole="button" onPress={() => void shortcuts.refetch()} style={styles.retry}>
            <AppText size={12.5} lineHeight={19} color={colors.brown}>
              바로가기를 불러오지 못했어요. 눌러서 다시 시도해요
            </AppText>
          </Pressable>
        ) : null}

        <AppText size={12.5} lineHeight={19} color={colors.brown} style={styles.or}>
          또는 날짜를 직접 골라보세요
        </AppText>

        <MonthCalendar
          year={view.year}
          month={view.month}
          markers={markers}
          today={today}
          selectedDate={choice?.kind === 'day' ? choice.date : undefined}
          size={calendarSize}
          canGoNext={monthIndex(view) < thisMonth}
          canGoPrev={monthIndex(view) > firstMonth}
          isSelectable={(date) => date <= today && date >= earliest}
          onChangeMonth={(delta) => setView((current) => addMonths(current.year, current.month, delta))}
          onSelectDate={(date) => setChoice({ kind: 'day', date })}
        />
        <View style={styles.legend}>
          <EmotionLegend />
        </View>

        <PickSummary choice={choice} shortcut={selectedShortcut} preview={dayPreview} />
      </ScrollView>

      <View style={[styles.footer, { paddingBottom: Math.max(insets.bottom, 12) }]}>
        <Button label="만나러 가기" disabled={!chatInput} loading={createChat.isPending} onPress={() => void meet()} />
      </View>
    </View>
  );
}

/** 연 · 월을 하나의 숫자로 바꿔 달 단위로 크기를 비교해요. */
function monthIndex({ year, month }: { year: number; month: number }): number {
  return year * 12 + month;
}

type SummaryProps = {
  choice: Choice | null;
  /** 고른 바로가기의 기록량(아직 받는 중이면 undefined) */
  shortcut: PersonaWindow | undefined;
  /** 캘린더에서 고른 날의 기록량 */
  preview: UseQueryResult<PersonaWindow>;
};

/** 고른 시점의 이름 · 감정 · 기록량(일기 건수 등)과 정확도 안내 카드 */
function PickSummary({ choice, shortcut, preview }: SummaryProps) {
  if (!choice) {
    return (
      <Card tint style={styles.summary}>
        <AppText size={13.5} lineHeight={22} color={colors.brown}>
          시점을 고르면 그 무렵 기록이 얼마나 있는지 알려줘요
        </AppText>
      </Card>
    );
  }

  const range = choice.kind === 'shortcut' ? shortcut : preview.data;
  if (!range) {
    if (choice.kind === 'day' && preview.isError) {
      return (
        <Card tint style={styles.summary}>
          <AppText size={13.5} lineHeight={22}>
            {errorMessage(preview.error)}
          </AppText>
          <Pressable accessibilityRole="button" onPress={() => void preview.refetch()} style={styles.retry}>
            <AppText weight="bold" size={13} lineHeight={20} color={colors.brown}>
              다시 불러오기
            </AppText>
          </Pressable>
        </Card>
      );
    }
    return (
      <Card tint style={styles.summary}>
        <AppText size={13.5} lineHeight={22} color={colors.muted}>
          기록을 살펴보는 중이에요…
        </AppText>
      </Card>
    );
  }

  const title = personaTitle({
    scope: range.scope,
    shortcutKey: choice.kind === 'shortcut' ? choice.key : null,
    anchorDate: range.anchorDate,
  });
  const counts = `일기 ${range.diaryCount}건${range.messengerWeeks > 0 ? ` · 메신저 ${range.messengerWeeks}주` : ''}`;
  const note =
    range.scope === 'DAY' ? levelNote(range) : `${windowText(range)} 기록으로 만나요. ${levelNote(range)}`;

  return (
    <Card tint style={styles.summary}>
      <View style={styles.summaryHead}>
        <AppText weight="bold" size={14.5} lineHeight={22}>
          {title}
        </AppText>
        {range.emotions?.map((emotion) => <EmotionBadge key={emotion} emotion={emotion} />)}
      </View>
      <AppText size={13.5} lineHeight={22}>
        {counts}
      </AppText>
      <AppText size={12.5} lineHeight={19} color={colors.brown} style={styles.note}>
        {note}
      </AppText>
    </Card>
  );
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
  // 가운데 제목이 정확히 가운데 오도록 왼쪽 뒤로 가기 아이콘(22)과 같은 폭을 비워 둬요
  navSide: {
    width: 22,
  },
  content: {
    paddingHorizontal: 22,
    paddingTop: 8,
    paddingBottom: 16,
  },
  title: {
    marginTop: 4,
  },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
    marginTop: 16,
  },
  retry: {
    marginTop: 8,
    alignSelf: 'flex-start',
  },
  or: {
    marginTop: 14,
    marginBottom: 8,
    textAlign: 'center',
  },
  legend: {
    marginTop: 8,
  },
  summary: {
    marginTop: 12,
    paddingVertical: 14,
    paddingHorizontal: 16,
  },
  summaryHead: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    columnGap: 8,
    rowGap: 4,
  },
  note: {
    marginTop: 4,
  },
  footer: {
    paddingHorizontal: 22,
    paddingTop: 8,
  },
});
