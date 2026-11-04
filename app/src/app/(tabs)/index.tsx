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
import { useDeleteDiary, useMonthDiaries } from '@/features/diary/hooks';
import { addMonths, formatKoreanDate, parseDateString, todayString } from '@/lib/date';
import { confirm, notify } from '@/lib/dialog';
import { useSession } from '@/session/SessionProvider';
import { colors, type Emotion } from '@/theme/colors';

/**
 * 스토리보드 A-3 홈: 일기 중심 화면. 감정 색 점이 찍힌 캘린더(좌우로 쓸어 달 넘기기)와,
 * 그 아래에 고른 날의 일기 패널이 붙어 있어요. 처음에는 오늘이 골라져 있어요.
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

  const markers: Record<string, Emotion[]> = {};
  for (const diary of viewDiaries.data ?? []) markers[diary.date] = diary.emotions;

  const selectedDiary = selectedMonthDiaries.data?.find((diary) => diary.date === selectedDate);
  const canGoNext = view.year * 12 + view.month < thisYear * 12 + thisMonth;

  function openDiary(date: string) {
    router.push({ pathname: '/diary/[date]', params: { date } });
  }

  function openChat(_date: string) {
    // 과거의 나와 대화(시점 분석 → 채팅)는 다음 단계에서 연결해요
    notify('곧 열려요', '이 날의 나와 대화하는 기능은 다음 단계에서 연결할게요.');
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
