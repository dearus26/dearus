import { useEffect, useRef, useState } from 'react';
import { Animated, PanResponder, Pressable, StyleSheet, View } from 'react-native';

import { AppText } from '@/components/AppText';
import { Icon } from '@/components/Icon';
import {
  WEEKDAY_LABELS,
  daysInMonth,
  firstWeekday,
  formatMonthDay,
  formatMonthTitle,
  toDateString,
} from '@/lib/date';
import { colors, emotionColors, type Emotion } from '@/theme/colors';

type Props = {
  year: number;
  /** 1~12 */
  month: number;
  /** 'YYYY-MM-DD' → 그날의 감정 태그. 일기가 있는 날만 넣어요(점이 찍혀요). 태그가 없는 일기는 빈 배열 */
  markers: Record<string, Emotion[] | undefined>;
  /** 오늘 날짜(진한 동그라미로 표시) */
  today: string;
  /** 지금 골라 둔 날짜(금색 바탕으로 표시). 없으면 표시하지 않아요. */
  selectedDate?: string;
  onSelectDate: (date: string) => void;
  /** 화살표를 누르거나 좌우로 쓸어 넘기면 불려요. -1은 이전 달, 1은 다음 달 */
  onChangeMonth: (delta: -1 | 1) => void;
  /** 다음 달로 넘길 수 있는지(미래 달은 볼 일이 없어서 홈에서는 막아요) */
  canGoNext?: boolean;
  /** 누를 수 있는 날인지. 기본은 오늘까지예요. */
  isSelectable?: (date: string) => boolean;
  /** 줄 높이 · 글자 크기. 기본은 regular */
  size?: CalendarSize;
};

/**
 * 달력 크기. 낮은 화면에서는 줄 높이를 줄여서 아래 일기 패널이 쓸 자리를 남겨요.
 * (화면 전체는 스크롤되지 않고, 일기 내용 칸만 스크롤되기 때문이에요)
 */
export type CalendarSize = 'regular' | 'compact' | 'tight';

const SIZES: Record<CalendarSize, { row: number; circle: number; font: number; dot: number }> = {
  regular: { row: 46, circle: 32, font: 17, dot: 7 },
  compact: { row: 40, circle: 28, font: 15.5, dot: 6 },
  tight: { row: 36, circle: 26, font: 14.5, dot: 6 },
};

/** 옆으로 이만큼(dp) 이상 끌거나 빠르게 쓸면 달이 넘어가요. */
const SWIPE_DISTANCE = 50;
const SWIPE_VELOCITY = 0.6;
/** 넘어갈 때 달력이 밀려나는 거리(dp) */
const SLIDE = 60;

/**
 * 스토리보드의 캘린더: 일~토, 날짜 아래에 그날 감정 색의 점.
 * 좌우로 쓸어 달을 넘길 수 있어요(스토리보드에 없던 요청). 월 이동 화살표도 함께 둬요.
 */
export function MonthCalendar({
  year,
  month,
  markers,
  today,
  selectedDate,
  onSelectDate,
  onChangeMonth,
  canGoNext = true,
  isSelectable = (date) => date <= today,
  size = 'regular',
}: Props) {
  const { translateX, changeMonth, panHandlers } = useMonthSwipe(canGoNext, onChangeMonth);
  const dims = SIZES[size];
  const weeks = buildWeeks(year, month);

  const animatedStyle = {
    // 웹(PC 브라우저)에서 마우스로 끌 때 글자 선택이 시작되면 제스처가 끊겨서, 달력은 선택을 막아요(폰 터치에는 영향 없어요)
    userSelect: 'none' as const,
    transform: [{ translateX }],
    opacity: translateX.interpolate({
      inputRange: [-SLIDE, 0, SLIDE],
      outputRange: [0.25, 1, 0.25],
      extrapolate: 'clamp',
    }),
  };

  return (
    <Animated.View style={animatedStyle} {...panHandlers}>
      <View style={styles.titleRow}>
        <Pressable accessibilityRole="button" accessibilityLabel="이전 달" hitSlop={14} onPress={() => changeMonth(-1)}>
          <Icon name="chevronLeft" size={20} color={colors.brown} />
        </Pressable>
        <AppText serif weight="bold" size={22} lineHeight={30}>
          {formatMonthTitle(year, month)}
        </AppText>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="다음 달"
          accessibilityState={{ disabled: !canGoNext }}
          disabled={!canGoNext}
          hitSlop={14}
          onPress={() => changeMonth(1)}
          style={{ opacity: canGoNext ? 1 : 0.25 }}>
          <Icon name="chevronRight" size={20} color={colors.brown} />
        </Pressable>
      </View>

      <View style={styles.row}>
        {WEEKDAY_LABELS.map((label) => (
          <View key={label} style={styles.weekdayCell}>
            <AppText weight="bold" size={12} lineHeight={18} color={colors.gold} style={styles.weekdayText}>
              {label}
            </AppText>
          </View>
        ))}
      </View>

      {weeks.map((week, weekIndex) => (
        <View key={weekIndex} style={styles.row}>
          {week.map((day, dayIndex) => {
            if (day === null) {
              return (
                <View key={dayIndex} style={[styles.cell, { height: dims.row }, dayIndex > 0 && styles.cellDivider]} />
              );
            }
            const date = toDateString(year, month, day);
            return (
              <DateCell
                key={dayIndex}
                date={date}
                day={day}
                dims={dims}
                column={dayIndex}
                isToday={date === today}
                isSelected={date === selectedDate}
                selectable={isSelectable(date)}
                emotions={markers[date]}
                onPress={() => onSelectDate(date)}
              />
            );
          })}
        </View>
      ))}
    </Animated.View>
  );
}

/**
 * 좌우로 쓸어 달을 넘기는 동작이에요. 손가락을 따라 달력이 살짝 움직이다가,
 * 기준을 넘으면 밀려나고 새 달이 반대쪽에서 들어와요. 세로로 쓰는 움직임은 건드리지 않아서 스크롤과 겹치지 않아요.
 */
function useMonthSwipe(canGoNext: boolean, onChangeMonth: (delta: -1 | 1) => void) {
  const [translateX] = useState(() => new Animated.Value(0));
  const busy = useRef(false);
  // 제스처 처리 함수는 한 번만 만들어서, 최신 속성은 ref로 읽어요
  const latest = useRef({ canGoNext, onChangeMonth });
  useEffect(() => {
    latest.current = { canGoNext, onChangeMonth };
  });

  const [swipe] = useState(() => {
    const snapBack = () => {
      Animated.spring(translateX, { toValue: 0, useNativeDriver: true, bounciness: 6 }).start();
    };

    const change = (direction: -1 | 1) => {
      if (busy.current) return;
      if (direction === 1 && !latest.current.canGoNext) {
        snapBack();
        return;
      }
      busy.current = true;
      const out = direction === 1 ? -SLIDE : SLIDE;
      Animated.timing(translateX, { toValue: out, duration: 110, useNativeDriver: true }).start(() => {
        latest.current.onChangeMonth(direction);
        translateX.setValue(-out);
        Animated.timing(translateX, { toValue: 0, duration: 170, useNativeDriver: true }).start(() => {
          busy.current = false;
        });
      });
    };

    const panResponder = PanResponder.create({
      // Capture: 손가락이 날짜 칸(눌림) 위에서 시작해도 가로로 쓸면 달력이 먼저 가져가요.
      // 가로 움직임이 확실히 더 클 때만 가져가서, 세로 스크롤과 날짜 누르기는 방해하지 않아요.
      onMoveShouldSetPanResponderCapture: (_event, gesture) =>
        Math.abs(gesture.dx) > 12 && Math.abs(gesture.dx) > Math.abs(gesture.dy) * 1.5,
      onPanResponderMove: (_event, gesture) => {
        if (busy.current) return;
        translateX.setValue(Math.max(-SLIDE, Math.min(SLIDE, gesture.dx * 0.5)));
      },
      onPanResponderRelease: (_event, gesture) => {
        if (gesture.dx < -SWIPE_DISTANCE || gesture.vx < -SWIPE_VELOCITY) change(1);
        else if (gesture.dx > SWIPE_DISTANCE || gesture.vx > SWIPE_VELOCITY) change(-1);
        else snapBack();
      },
      onPanResponderTerminate: snapBack,
    });

    return { change, panHandlers: panResponder.panHandlers };
  });

  return { translateX, changeMonth: swipe.change, panHandlers: swipe.panHandlers };
}

type DateCellProps = {
  date: string;
  day: number;
  dims: (typeof SIZES)[CalendarSize];
  column: number;
  isToday: boolean;
  isSelected: boolean;
  selectable: boolean;
  emotions: Emotion[] | undefined;
  onPress: () => void;
};

function DateCell({ date, day, dims, column, isToday, isSelected, selectable, emotions, onPress }: DateCellProps) {
  const dotColor = emotions && emotions.length > 0 ? emotionColors[emotions[0]] : colors.gold;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${formatMonthDay(date)}${emotions ? ', 일기 있음' : ''}`}
      accessibilityState={{ disabled: !selectable, selected: isSelected }}
      disabled={!selectable}
      onPress={onPress}
      style={[styles.cell, { height: dims.row }, column > 0 && styles.cellDivider, !selectable && styles.future]}>
      <View
        style={[
          styles.dayCircle,
          { width: dims.circle, height: dims.circle, borderRadius: dims.circle / 2 },
          isSelected && styles.selected,
          isToday && styles.today,
        ]}>
        <AppText weight={isToday || isSelected ? 'bold' : 'medium'} size={dims.font} lineHeight={Math.round(dims.font * 1.3)}>
          {day}
        </AppText>
      </View>
      {emotions ? (
        <View
          style={[
            styles.dot,
            { width: dims.dot, height: dims.dot, borderRadius: dims.dot / 2, backgroundColor: dotColor },
          ]}
        />
      ) : null}
    </Pressable>
  );
}

/** 그 달을 7칸씩 끊은 주 목록. 달의 앞뒤 빈칸은 null이에요. */
function buildWeeks(year: number, month: number): (number | null)[][] {
  const leading = firstWeekday(year, month);
  const cells: (number | null)[] = [
    ...Array<null>(leading).fill(null),
    ...Array.from({ length: daysInMonth(year, month) }, (_, index) => index + 1),
  ];
  while (cells.length % 7 !== 0) cells.push(null);
  const weeks: (number | null)[][] = [];
  for (let i = 0; i < cells.length; i += 7) weeks.push(cells.slice(i, i + 7));
  return weeks;
}

const styles = StyleSheet.create({
  titleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 40,
    marginTop: 2,
    marginBottom: 8,
  },
  row: {
    flexDirection: 'row',
  },
  weekdayCell: {
    flex: 1,
    paddingLeft: 4,
    marginBottom: 2,
  },
  weekdayText: {
    letterSpacing: 0.4,
  },
  // 줄 높이는 size에 따라 고정해서(위에서 height를 넣어줘요) 점이 있든 없든 한 달의 높이가 흔들리지 않아요
  cell: {
    flex: 1,
    paddingTop: 2,
    paddingLeft: 4,
    borderTopWidth: 1,
    borderTopColor: 'rgba(111, 81, 57, 0.16)',
  },
  cellDivider: {
    borderLeftWidth: 1,
    borderLeftColor: 'rgba(111, 81, 57, 0.12)',
  },
  future: {
    opacity: 0.4,
  },
  dayCircle: {
    alignItems: 'center',
    justifyContent: 'center',
  },
  selected: {
    backgroundColor: 'rgba(217, 168, 105, 0.4)',
  },
  today: {
    borderWidth: 2,
    borderColor: colors.ink,
  },
  dot: {
    marginTop: 2,
    marginLeft: 4,
  },
});
