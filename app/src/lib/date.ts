/**
 * 날짜는 서버와 같은 'YYYY-MM-DD' 문자열로 주고받아요(명세: 날짜만 필요하면 `2026-10-06`).
 * 시간대 문제를 피하려고 Date 객체를 문자열로 바꿀 때는 폰의 현지 날짜(연 · 월 · 일)를 그대로 써요.
 */

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토'] as const;
export const WEEKDAY_LABELS = WEEKDAYS;

export function pad(n: number): string {
  return String(n).padStart(2, '0');
}

/** month는 1~12예요. */
export function toDateString(year: number, month: number, day: number): string {
  return `${year}-${pad(month)}-${pad(day)}`;
}

/** Date 객체의 폰 현지 날짜 */
export function dateStringOf(date: Date): string {
  return toDateString(date.getFullYear(), date.getMonth() + 1, date.getDate());
}

export function todayString(): string {
  return dateStringOf(new Date());
}

export function parseDateString(value: string): { year: number; month: number; day: number } {
  const [year, month, day] = value.split('-').map(Number);
  return { year, month, day };
}

/** 'YYYY-MM-DD' 모양이면서 실제로 있는 날짜인지 확인해요(예: 2026-02-31은 아니에요). */
export function isValidDateString(value: unknown): value is string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const { year, month, day } = parseDateString(value);
  const date = new Date(year, month - 1, day);
  return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day;
}

export function weekdayOf(value: string): number {
  const { year, month, day } = parseDateString(value);
  return new Date(year, month - 1, day).getDay();
}

/** 예: 2026년 10월 6일 화요일 */
export function formatKoreanDate(value: string): string {
  const { year, month, day } = parseDateString(value);
  return `${year}년 ${month}월 ${day}일 ${WEEKDAYS[weekdayOf(value)]}요일`;
}

/** 예: 10월 6일 */
export function formatMonthDay(value: string): string {
  const { month, day } = parseDateString(value);
  return `${month}월 ${day}일`;
}

export function formatMonthTitle(year: number, month: number): string {
  return `${year}년 ${month}월`;
}

export function daysInMonth(year: number, month: number): number {
  return new Date(year, month, 0).getDate();
}

/** 그 달 1일의 요일(0=일요일) */
export function firstWeekday(year: number, month: number): number {
  return new Date(year, month - 1, 1).getDay();
}

export function addMonths(year: number, month: number, delta: number): { year: number; month: number } {
  const date = new Date(year, month - 1 + delta, 1);
  return { year: date.getFullYear(), month: date.getMonth() + 1 };
}

export function addDays(value: string, delta: number): string {
  const { year, month, day } = parseDateString(value);
  const date = new Date(year, month - 1, day + delta);
  return toDateString(date.getFullYear(), date.getMonth() + 1, date.getDate());
}

/** from에서 to까지 며칠인지(to가 더 나중이면 양수) */
export function daysBetween(from: string, to: string): number {
  const toMs = (value: string) => {
    const { year, month, day } = parseDateString(value);
    return Date.UTC(year, month - 1, day);
  };
  return Math.round((toMs(to) - toMs(from)) / 86_400_000);
}

/** 목록에 쓰는 "방금", "3시간 전", "어제", "3일 전", "1주 전". 한 달이 넘으면 날짜로 보여줘요. */
export function formatRelative(iso: string, now: Date = new Date()): string {
  const then = new Date(iso);
  const minutes = Math.floor((now.getTime() - then.getTime()) / 60_000);
  if (minutes < 1) return '방금';
  if (minutes < 60) return `${minutes}분 전`;
  const thenDate = dateStringOf(then);
  const days = daysBetween(thenDate, dateStringOf(now));
  if (days <= 0) return `${Math.floor(minutes / 60)}시간 전`;
  if (days === 1) return '어제';
  if (days < 7) return `${days}일 전`;
  if (days < 30) return `${Math.floor(days / 7)}주 전`;
  return formatMonthDay(thenDate);
}

/** 그 달의 첫날과 마지막 날(GET /diaries의 from · to) */
export function monthRange(year: number, month: number): { from: string; to: string } {
  return { from: toDateString(year, month, 1), to: toDateString(year, month, daysInMonth(year, month)) };
}

/** 날짜 문자열은 연-월-일 순서라서 글자 비교가 곧 날짜 비교예요. */
export function isFuture(value: string, today: string = todayString()): boolean {
  return value > today;
}
