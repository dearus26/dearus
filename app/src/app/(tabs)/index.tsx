import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, StyleSheet, View, useWindowDimensions } from 'react-native';
import Svg, { Circle, Path } from 'react-native-svg';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import type { Diary } from '@/api/diaries';
import { errorMessage } from '@/api/errors';
import { AppText } from '@/components/AppText';
import { EmotionLegend } from '@/components/EmotionLegend';
import { MonthCalendar } from '@/components/MonthCalendar';
import { DayPanel } from '@/features/diary/DayPanel';
import { useDeleteDiary, useMonthCalendar, useMonthDiaries } from '@/features/diary/hooks';
import { addMonths, formatKoreanDate, parseDateString, todayString } from '@/lib/date';
import { confirm, notify } from '@/lib/dialog';
import { useSession } from '@/session/SessionProvider';
import { colors, type Emotion } from '@/theme/colors';

/**
 * 스토리보드 A-3 홈: 일기 중심 화면. 감정 색 점이 찍힌 캘린더(좌우로 쓸어 달 넘기기)와,
 * 그 아래에 고른 날의 일기 패널이 붙어 있어요. 처음에는 오늘이 골라져 있어요.
 * 메신저만 올린 날도 AI가 추천한 감정 색의 테두리 점으로 보여요(GET /calendar, 2026.11.28).
 */
export default function HomeScreen() {
  const insets = useSafeAreaInsets();
  const { user } = useSession();
  // 화면 전체는 스크롤되지 않아서, 낮은 폰에서는 달력을 줄여 일기 내용 칸이 쓸 자리를 남겨요
  const { height: windowHeight } = useWindowDimensions();
  const calendarSize = windowHeight >= 760 ? 'regular' : windowHeight >= 680 ? 'compact' : 'tight';

  const today = todayString();
  const { year: thisYear, month: thisMonth } = parseDateString(today);
  const [view, setView] = useState({ year: thisYear, month: thisMonth });
  const [selectedDate, setSelectedDate] = useState(today);

  const viewDiaries = useMonthDiaries(view.year, view.month);
  // 다른 달을 보다가 달을 넘겨도 패널은 고른 날을 그대로 보여줘야 해서, 고른 날이 속한 달을 따로 읽어요
  // (같은 달이면 위와 같은 요청이라 한 번만 가져와요)
  const selected = parseDateString(selectedDate);
  const selectedMonthDiaries = useMonthDiaries(selected.year, selected.month);
  const deleteDiary = useDeleteDiary();
  // 홈 캘린더(2026.11.28): 일기 날은 꽉 찬 점(직접 고른 감정), 메신저만 올린 날은 테두리 점(AI 추천 감정)이에요
  const viewCalendar = useMonthCalendar(view.year, view.month);
  const selectedMonthCalendar = useMonthCalendar(selected.year, selected.month);

  const markers: Record<string, Emotion[]> = {};
  const suggested: Record<string, Emotion[]> = {};
  if (viewCalendar.data) {
    for (const day of viewCalendar.data) {
      if (day.hasDiary) markers[day.date] = day.emotions;
      else if (day.hasMessenger) suggested[day.date] = day.suggestedEmotions;
    }
  } else {
    // 캘린더를 못 받으면(예: 서버가 아직 예전 버전) 일기 점만이라도 보여요
    for (const diary of viewDiaries.data ?? []) markers[diary.date] = diary.emotions;
  }

  const selectedDiary = selectedMonthDiaries.data?.find((diary) => diary.date === selectedDate);
  const selectedCalendarDay = selectedMonthCalendar.data?.find((day) => day.date === selectedDate);
  const canGoNext = view.year * 12 + view.month < thisYear * 12 + thisMonth;

  function openDiary(date: string) {
    router.push({ pathname: '/diary/[date]', params: { date } });
  }

  /** 시점 고르기를 그날 하루(DAY)가 골라진 채로 열어요. 기록량 확인과 "만나러 가기"는 그 화면에서 해요. */
  function openChat(date: string) {
    router.push({ pathname: '/persona/pick', params: { date } });
  }

  async function removeDiary(diary: Diary) {
    const ok = await confirm({
      title: '일기를 지울까요?',
      message: '지운 일기는 되돌릴 수 없어요.',
      confirmLabel: '지우기',
      destructive: true,
    });
    if (!ok) return;
    try {
      await deleteDiary.mutateAsync(diary.id);
    } catch (error) {
      notify('일기를 지우지 못했어요', errorMessage(error));
    }
  }

  return (
    <View style={[styles.screen, { paddingTop: insets.top }]}>
      <View style={styles.header}>
        <View style={styles.greeting}>
          <AppText size={12.5} lineHeight={19} color={colors.brown}>
            {formatKoreanDate(today)}
          </AppText>
          <AppText serif weight="bold" size={19} lineHeight={25} style={styles.greetingTitle}>
            {`${user?.nickname ?? ''}님, 오늘도 한 줄 남겨요`}
          </AppText>
        </View>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="설정"
          hitSlop={8}
          onPress={() => router.push('/settings')}
          style={styles.avatar}>
          <Svg width={20} height={20} viewBox="0 0 24 24" fill="none">
            <Circle cx={12} cy={8} r={4} stroke={colors.brown} strokeWidth={2} />
            <Path d="M4 21a8 8 0 0 1 16 0" stroke={colors.brown} strokeWidth={2} strokeLinecap="round" />
          </Svg>
        </Pressable>
      </View>

      {/* 화면 전체는 스크롤되지 않아요. 달력 · 범례는 고정이고, 패널이 남는 높이를 채우면서 일기 내용만 그 안에서 스크롤돼요 */}
      <View style={styles.content}>
        <MonthCalendar
          year={view.year}
          month={view.month}
          markers={markers}
          suggested={suggested}
          today={today}
          selectedDate={selectedDate}
          canGoNext={canGoNext}
          size={calendarSize}
          onChangeMonth={(delta) => setView((current) => addMonths(current.year, current.month, delta))}
          onSelectDate={setSelectedDate}
        />

        {calendarSize === 'tight' ? null : (
          <View style={styles.legend}>
            <EmotionLegend />
          </View>
        )}

        <DayPanel
          style={styles.panel}
          date={selectedDate}
          diary={selectedDiary}
          calendarDay={selectedCalendarDay}
          loading={selectedMonthDiaries.isPending}
          failed={selectedMonthDiaries.isError}
          deleting={deleteDiary.isPending}
          onRetry={() => void selectedMonthDiaries.refetch()}
          onWrite={openDiary}
          onDelete={removeDiary}
          onChat={openChat}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: colors.cream,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 22,
    paddingTop: 12,
    paddingBottom: 8,
  },
  greeting: {
    flex: 1,
    paddingRight: 12,
  },
  greetingTitle: {
    marginTop: 4,
  },
  avatar: {
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: colors.panel,
    borderWidth: 1,
    borderColor: colors.beige,
    alignItems: 'center',
    justifyContent: 'center',
  },
  content: {
    flex: 1,
    paddingHorizontal: 22,
    paddingTop: 4,
    paddingBottom: 12,
  },
  legend: {
    marginTop: 8,
  },
  // 남는 세로 공간을 모두 차지해요(낮은 폰에서는 그만큼 작아지고, 일기 내용 칸이 그 안에서 스크롤돼요)
  panel: {
    flex: 1,
    marginTop: 12,
  },
});
