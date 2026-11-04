import type { ChatListItem } from '@/api/chats';
import type { PersonaWindow, ShortcutKey } from '@/api/personas';
import { formatMonthDay, parseDateString, todayString } from '@/lib/date';

/**
 * 올해 날짜는 "10월 4일", 작년이면 "2025년 10월 4일". 1년 전 시점이 올해의 미래 날짜처럼 읽히지 않게 연도를 붙여요.
 * (시점은 최대 1년 전까지라서 연도가 다른 경우는 작년뿐이에요)
 */
function formatPointInTime(date: string): string {
  const { year } = parseDateString(date);
  return year === parseDateString(todayString()).year ? formatMonthDay(date) : `${year}년 ${formatMonthDay(date)}`;
}

/** 바로가기 이름. 칩("1개월 전") · 제목("1개월 전의 나") · 목록 동그라미("1개월") · 불러오는 화면의 동그라미(두 줄)로 써요. */
export const shortcutLabels: Record<ShortcutKey, { chip: string; title: string; badge: string; ring: string }> = {
  ONE_MONTH: { chip: '1개월 전', title: '1개월 전의 나', badge: '1개월', ring: '1개월\n전' },
  SIX_MONTHS: { chip: '6개월 전', title: '6개월 전의 나', badge: '6개월', ring: '6개월\n전' },
  ONE_YEAR: { chip: '1년 전', title: '1년 전의 나', badge: '1년', ring: '1년\n전' },
};

/** 화면에 보여주는 순서(가까운 시점부터) */
export const shortcutOrder: ShortcutKey[] = ['ONE_MONTH', 'SIX_MONTHS', 'ONE_YEAR'];

/** 세션이 어느 시점인지 알려주는 값. 목록 항목 · 세션 · 기록량이 모두 이 모양을 갖고 있어요. */
type Target = Pick<ChatListItem, 'scope' | 'shortcutKey' | 'anchorDate'>;

/** "1개월 전의 나", "10월 4일의 나". 명세에 따라 제목은 앱이 scope · anchorDate로 만들어요. */
export function personaTitle({ scope, shortcutKey, anchorDate }: Target): string {
  if (shortcutKey) return shortcutLabels[shortcutKey].title;
  return personaDateTitle({ scope, anchorDate });
}

/** 대화 첫머리 구분선: "7월 6일 무렵의 나"(전후 2주), "10월 4일의 나"(그날 하루). 바로가기여도 날짜로 보여줘요. */
export function personaDateTitle({ scope, anchorDate }: Pick<Target, 'scope' | 'anchorDate'>): string {
  return scope === 'DAY' ? `${formatPointInTime(anchorDate)}의 나` : `${formatPointInTime(anchorDate)} 무렵의 나`;
}

/** 대화 화면 위 제목 아래 글: "2026년 7월 무렵"(전후 2주), "2026년 10월 4일"(그날 하루) */
export function personaPeriod({ scope, anchorDate }: Pick<Target, 'scope' | 'anchorDate'>): string {
  const { year, month, day } = parseDateString(anchorDate);
  return scope === 'DAY' ? `${year}년 ${month}월 ${day}일` : `${year}년 ${month}월 무렵`;
}

/** 대화 상단 정확도(명세 RecordLevel 표의 "대화 상단 정확도"): 기록 없음 · 부족은 낮음, 충분은 보통, 풍부는 높음 */
export function accuracyLabel(level: PersonaWindow['level']): string {
  return level === 'RICH' ? '높음' : level === 'ENOUGH' ? '보통' : '낮음';
}

/** 목록 · 불러오는 화면의 동그라미 글자: 바로가기는 "1개월", 직접 고른 날은 "10/4" */
export function personaBadge({ shortcutKey, anchorDate }: Target): string {
  if (shortcutKey) return shortcutLabels[shortcutKey].badge;
  const { month, day } = parseDateString(anchorDate);
  return `${month}/${day}`;
}

/** 불러오는 화면 큰 동그라미 속 글자: "3개월\n전", "10월\n4일" */
export function personaRingLabel({ shortcutKey, anchorDate }: Target): string {
  if (shortcutKey) return shortcutLabels[shortcutKey].ring;
  const { month, day } = parseDateString(anchorDate);
  return `${month}월\n${day}일`;
}

/** "9월 7일 ~ 10월 5일", 하루면 "10월 4일". 작년이면 "2025년 10월 27일 ~ 11월 24일"처럼 연도가 붙어요. */
export function windowText({ windowStart, windowEnd }: Pick<PersonaWindow, 'windowStart' | 'windowEnd'>): string {
  if (windowStart === windowEnd) return formatPointInTime(windowStart);
  const sameYear = parseDateString(windowStart).year === parseDateString(windowEnd).year;
  return `${formatPointInTime(windowStart)} ~ ${sameYear ? formatMonthDay(windowEnd) : formatPointInTime(windowEnd)}`;
}

/** 시점 고르기 카드의 기록량 안내(명세 RecordLevel 표의 "시점 고르기 문구"를 풀어 쓴 것) */
export function levelNote({ scope, level }: Pick<PersonaWindow, 'scope' | 'level'>): string {
  if (scope === 'DAY') {
    return level === 'NONE'
      ? '이 날은 쓴 일기가 없어요. 기본 말투로 만나고, 정확도가 낮을 수 있어요'
      : '그날 하루의 기록만으로 그때의 나를 만나요. 전후 기록이 적어 정확도는 보통일 수 있어요';
  }
  switch (level) {
    case 'NONE':
      return '그 무렵 쓴 기록이 없어요. 기본 말투로 만나고, 정확도가 낮을 수 있어요';
    case 'LOW':
      return '그 무렵 기록이 부족해서 정확도가 낮을 수 있어요';
    case 'ENOUGH':
      return '그 무렵 기록이 충분해요';
    case 'RICH':
      return '그 무렵 기록이 풍부해요';
  }
}
