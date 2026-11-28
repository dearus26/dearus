import { router } from 'expo-router';
import { useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import type { CoupleDay, CoupleState } from '@/api/couples';
import { errorMessage } from '@/api/errors';
import type { ShortcutKey } from '@/api/personas';
import { AppText } from '@/components/AppText';
import { Button } from '@/components/Button';
import { Card } from '@/components/Card';
import { ChoiceChip } from '@/components/ChoiceChip';
import { MonthCalendar } from '@/components/MonthCalendar';
import { Tag } from '@/components/Tag';
import { CouplePage } from '@/features/couple/CouplePage';
import { PairHeader } from '@/features/couple/PairHeader';
import { RevokeLink } from '@/features/couple/RevokeLink';
import { useCoupleDays } from '@/features/couple/hooks';
import { useShortcuts } from '@/features/past/hooks';
import { shortcutLabels, shortcutOrder, windowText } from '@/features/past/labels';
import {
  addDays,
  addMonths,
  formatMonthDay,
  formatYearMonthDay,
  monthRange,
  parseDateString,
  todayString,
} from '@/lib/date';
import { colors, emotionColors, emotionLabels, type Emotion } from '@/theme/colors';

/** 한 번에 받을 수 있는 최대 일수(명세 GET /couples/days) */
const MAX_RANGE_DAYS = 93;
/** 오늘로부터 이만큼 전까지 고를 수 있어요(명세: 1년 전까지) */
const MAX_DAYS_AGO = 365;

/** 추천한 날(또는 캘린더에서 고른 날) 하나, 아니면 바로가기 칩 하나. 둘 중 하나만 골라져 있어요. */
type Choice = { kind: 'day'; date: string } | { kind: 'shortcut'; key: ShortcutKey };

type Props = {
  state: CoupleState;
  refreshing: boolean;
  onRefresh: () => void;
  onRevoke: () => void;
};

const bothAngry = (day: CoupleDay) => day.myEmotions.includes('ANGRY') && day.partnerEmotions.includes('ANGRY');

/** 서버가 추천한 날 중 둘 다 화난 날을 먼저, 그다음 가장 최근 날을 맨 위 카드로 보여줘요. */
function pickFeatured(days: CoupleDay[]): CoupleDay | null {
  const recommended = days.filter((day) => day.recommended);
  recommended.sort((a, b) => Number(bothAngry(b)) - Number(bothAngry(a)) || b.date.localeCompare(a.date));
  return recommended[0] ?? null;
}

function emotionsText(emotions: Emotion[]): string {
  return emotions.length > 0 ? emotions.map((emotion) => emotionLabels[emotion]).join(' · ') : '감정 없음';
}

/** 카드 색 띠: 둘 다 화난 날은 화남 색, 그 밖에는 두 사람 감정 중 처음 나오는 어두운 감정 색이에요. */
function accentOf(day: CoupleDay): string {
  if (bothAngry(day)) return emotionColors.ANGRY;
  const negative = [...day.myEmotions, ...day.partnerEmotions].find((emotion) =>
    (['ANGRY', 'SAD', 'ANXIOUS'] as Emotion[]).includes(emotion),
  );
  return negative ? emotionColors[negative] : colors.beige;
}

function dayTitle(date: string): string {
  const sameYear = parseDateString(date).year === parseDateString(todayString()).year;
  return sameYear ? formatMonthDay(date) : formatYearMonthDay(date);
}

/**
 * 스토리보드 C-3 함께 볼 날 고르기. 두 사람의 기록(일기 또는 그날 올린 메신저 대화)이 모두 있는 날만 비교할 수 있고, 감정이 엇갈린 날(특히 다툰 날)은
 * 서버가 추천해서 맨 위 카드로 보여줘요. 1개월 · 6개월 · 1년 전 바로가기나 "직접 고르기"(캘린더)로 다른 시점도 고를 수 있어요.
 * 고르고 "두 시점 비교하러 가기"를 누르면 두 시점 비교 화면(C-4)에서 각자 그날의 연인과 대화를 시작해요.
 */
export function DayPickView({ state, refreshing, onRefresh, onRevoke }: Props) {
  const today = todayString();
  const earliest = addDays(today, -MAX_DAYS_AGO);
  const myName = state.me?.nickname ?? '나';
  const partnerName = state.partner?.nickname ?? '상대';

  const [choice, setChoice] = useState<Choice | null>(null);
  const [manual, setManual] = useState(false);
  const [view, setView] = useState(() => {
    const { year, month } = parseDateString(today);
    return { year, month };
  });

  const recent = useCoupleDays(addDays(today, -(MAX_RANGE_DAYS - 1)), today, true);
  const shortcuts = useShortcuts();

  // "직접 고르기"의 캘린더가 보여주는 달: 오늘 · 1년 전 안쪽만 불러요
  const { from: monthFrom, to: monthTo } = monthRange(view.year, view.month);
  const rangeFrom = monthFrom < earliest ? earliest : monthFrom;
  const rangeTo = monthTo > today ? today : monthTo;
  const monthDays = useCoupleDays(rangeFrom, rangeTo, manual && rangeFrom <= rangeTo);
  const markers: Record<string, Emotion[]> = {};
  const selectable = new Set<string>();
  for (const day of monthDays.data ?? []) {
    markers[day.date] = day.myEmotions;
    selectable.add(day.date);
  }

  const featured = pickFeatured(recent.data ?? []);
  // 아무것도 안 골랐으면 추천한 날이 골라져 있는 걸로 봐요(스토리보드의 테두리 있는 카드)
  const effective: Choice | null = choice ?? (featured ? { kind: 'day', date: featured.date } : null);
  const selectedShortcut =
    effective?.kind === 'shortcut' ? shortcuts.data?.find((item) => item.key === effective.key) : undefined;
  const canGo = effective?.kind === 'day' || (effective?.kind === 'shortcut' && selectedShortcut !== undefined);

  const thisMonth = parseDateString(today).year * 12 + parseDateString(today).month;
  const firstMonth = parseDateString(earliest).year * 12 + parseDateString(earliest).month;
  const viewMonth = view.year * 12 + view.month;

  function compare() {
    if (effective?.kind === 'day') {
      router.push({ pathname: '/couples/compare', params: { date: effective.date, scope: 'DAY' } });
    } else if (selectedShortcut) {
      router.push({
        pathname: '/couples/compare',
        params: { date: selectedShortcut.anchorDate, scope: selectedShortcut.scope },
      });
    }
  }

  return (
    <CouplePage
      refreshing={refreshing}
      onRefresh={onRefresh}
      footer={<Button label="두 시점 비교하러 가기" disabled={!canGo} onPress={compare} />}>
      <PairHeader
        me={{ name: myName, tag: '동의 완료', tone: 'ok' }}
        partner={{ name: partnerName, tag: '동의 완료', tone: 'ok' }}
        linked
      />
      <AppText serif weight="bold" size={26} lineHeight={36} style={styles.title}>
        {'그날의 우리,\n같이 볼까요?'}
      </AppText>
      <AppText size={12.5} lineHeight={19} color={colors.brown} style={styles.sub}>
        두 사람의 기록이 모두 있는 날만 비교할 수 있어요
      </AppText>

      {featured ? (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={`추천하는 날 ${dayTitle(featured.date)}`}
          accessibilityState={{ selected: effective?.kind === 'day' && effective.date === featured.date }}
          onPress={() => setChoice({ kind: 'day', date: featured.date })}>
          <Card
            style={[
              styles.featured,
              { borderColor: accentOf(featured) },
              effective?.kind === 'day' && effective.date === featured.date && styles.featuredOn,
            ]}>
            <View style={styles.featuredHead}>
              <AppText serif weight="bold" size={19} lineHeight={27} style={styles.grow}>
                {dayTitle(featured.date)}
              </AppText>
              <Tag label="기록 충분" ok />
            </View>
            <View style={[styles.reason, { backgroundColor: accentOf(featured) }]}>
              <AppText weight="bold" size={11} lineHeight={16} color={colors.white}>
                {bothAngry(featured)
                  ? '둘 다 화남'
                  : `${myName} ${emotionsText(featured.myEmotions)} · ${partnerName} ${emotionsText(featured.partnerEmotions)}`}
              </AppText>
            </View>
            <AppText size={12.5} lineHeight={19} color={colors.brown} style={styles.featuredNote}>
              {'그날, 서로 다르게 기억하고 있을지도 몰라요.\n같은 하루를 각자의 기록으로 다시 만나보세요.'}
            </AppText>
          </Card>
        </Pressable>
      ) : (
        <Card tint style={styles.featured}>
          <AppText size={13.5} lineHeight={21} color={colors.brown}>
            {recent.isPending
              ? '함께 볼 날을 찾는 중이에요…'
              : recent.isError
                ? errorMessage(recent.error)
                : '감정이 엇갈린 날은 아직 없어요. 아래에서 다른 시점이나 날짜를 골라 보세요.'}
          </AppText>
          {recent.isError ? (
            <Pressable accessibilityRole="button" onPress={() => void recent.refetch()} style={styles.retry}>
              <AppText weight="bold" size={13} lineHeight={20} color={colors.brown}>
                다시 불러오기
              </AppText>
            </Pressable>
          ) : null}
        </Card>
      )}

      <AppText size={12.5} lineHeight={19} color={colors.brown} style={styles.other}>
        다른 시점으로도 비교할 수 있어요
      </AppText>
      <View style={styles.chips} accessibilityRole="radiogroup">
        {shortcutOrder.map((key) => (
          <ChoiceChip
            key={key}
            label={shortcutLabels[key].chip}
            selected={effective?.kind === 'shortcut' && effective.key === key}
            disabled={!shortcuts.data}
            onPress={() => {
              setManual(false);
              setChoice({ kind: 'shortcut', key });
            }}
          />
        ))}
        <ChoiceChip label="직접 고르기" selected={manual} onPress={() => setManual((open) => !open)} />
      </View>

      {selectedShortcut ? (
        <Card tint style={styles.shortcutNote}>
          <AppText weight="bold" size={14} lineHeight={21}>
            {`${windowText(selectedShortcut)} 무렵의 두 사람`}
          </AppText>
          <AppText size={12.5} lineHeight={19} color={colors.brown}>
            그 무렵 기록으로 각자 그때의 연인을 만나고, 두 사람의 기억을 나란히 봐요
          </AppText>
        </Card>
      ) : null}

      {manual ? (
        <View style={styles.calendar}>
          <AppText size={12.5} lineHeight={19} color={colors.brown} style={styles.calendarNote}>
            두 사람의 기록이 모두 있는 날만 누를 수 있어요
          </AppText>
          <MonthCalendar
            year={view.year}
            month={view.month}
            markers={markers}
            today={today}
            selectedDate={effective?.kind === 'day' ? effective.date : undefined}
            size="compact"
            canGoNext={viewMonth < thisMonth}
            canGoPrev={viewMonth > firstMonth}
            isSelectable={(date) => selectable.has(date)}
            onChangeMonth={(delta) => setView((current) => addMonths(current.year, current.month, delta))}
            onSelectDate={(date) => setChoice({ kind: 'day', date })}
          />
          {monthDays.isError ? (
            <Pressable accessibilityRole="button" onPress={() => void monthDays.refetch()} style={styles.retry}>
              <AppText size={12.5} lineHeight={19} color={colors.brown}>
                이 달을 불러오지 못했어요. 눌러서 다시 시도해요
              </AppText>
            </Pressable>
          ) : null}
        </View>
      ) : null}

      <RevokeLink onPress={onRevoke} />
    </CouplePage>
  );
}

const styles = StyleSheet.create({
  title: {
    marginTop: 18,
  },
  sub: {
    marginTop: 6,
  },
  grow: {
    flex: 1,
  },
  featured: {
    marginTop: 14,
  },
  featuredOn: {
    borderWidth: 2,
  },
  featuredHead: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  reason: {
    alignSelf: 'flex-start',
    marginTop: 10,
    paddingHorizontal: 8,
    paddingVertical: 2,
    borderRadius: 999,
  },
  featuredNote: {
    marginTop: 8,
  },
  other: {
    marginTop: 16,
    marginBottom: 6,
  },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  shortcutNote: {
    marginTop: 12,
    paddingVertical: 12,
    paddingHorizontal: 16,
    gap: 2,
  },
  calendar: {
    marginTop: 12,
  },
  calendarNote: {
    marginBottom: 6,
    textAlign: 'center',
  },
  retry: {
    marginTop: 8,
    alignSelf: 'flex-start',
  },
});
