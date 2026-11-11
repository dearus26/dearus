import type { MessengerMessage } from '@/api/uploads';
import type { ParsedMessage } from '@/features/messenger/kakaoTxt';
import { mondayOf, todayString } from '@/lib/date';

/** 서버 한도(명세 POST /uploads/messenger): 메시지 5,000개, 한 메시지 2,000자, 요청 1MB, 60주(약 1년) */
const MAX_MESSAGES = 5000;
const MAX_TEXT = 2000;
const MAX_WEEKS = 60;
/** 요청 전체 1MB에서 여유를 둔 값(글자는 UTF-8 바이트로 세요) */
const MAX_BYTES = 900_000;
/** 메시지 하나의 JSON 군더더기({"date":"2026-11-03","text":""},) */
const OVERHEAD_BYTES = 40;

export type UploadPlan = {
  /** 서버로 보낼 내 말(오래된 것부터) */
  messages: MessengerMessage[];
  periodStart: string;
  periodEnd: string;
  weekCount: number;
  /** 한도 때문에 줄이거나 뺀 것을 알려주는 문구들(없으면 빈 배열) */
  notes: string[];
};

/**
 * 내 말을 서버 한도에 맞춰요. 넘치면 가장 최근 것을 남기고 오래된 것부터 빼고, 무엇을 뺐는지 notes에 적어요.
 * 보낼 말이 하나도 없으면 null이에요.
 */
export function planTxtUpload(mine: ParsedMessage[], today: string = todayString()): UploadPlan | null {
  const notes: string[] = [];
  const encoder = new TextEncoder();

  // 오늘 이후 날짜는 서버가 받지 않아요(휴대폰 시계가 틀렸거나 파일을 고친 경우)
  let kept = mine.filter((message) => message.date <= today);
  if (kept.length < mine.length) notes.push(`오늘 이후 날짜의 말 ${mine.length - kept.length}개는 빼요`);

  // 날짜순으로 정렬해요(정렬은 같은 날짜끼리 원래 순서를 지켜요)
  kept = [...kept].sort((a, b) => a.date.localeCompare(b.date));

  // 가장 최근 60주만
  const weeks = [...new Set(kept.map((message) => mondayOf(message.date)))];
  if (weeks.length > MAX_WEEKS) {
    const cutoff = weeks[weeks.length - MAX_WEEKS];
    const before = kept.length;
    kept = kept.filter((message) => mondayOf(message.date) >= cutoff);
    notes.push(`최근 ${MAX_WEEKS}주(약 1년)만 올려요. 더 오래된 말 ${before - kept.length}개는 빼요`);
  }

  // 가장 최근 5,000개만
  if (kept.length > MAX_MESSAGES) {
    notes.push(`최근 ${MAX_MESSAGES.toLocaleString('ko-KR')}개만 올려요. 더 오래된 말 ${kept.length - MAX_MESSAGES}개는 빼요`);
    kept = kept.slice(kept.length - MAX_MESSAGES);
  }

  // 한 메시지 2,000자까지(넘으면 잘라요)
  let truncated = 0;
  const limited = kept.map((message) => {
    if (message.text.length <= MAX_TEXT) return message;
    truncated += 1;
    return { ...message, text: message.text.slice(0, MAX_TEXT) };
  });
  if (truncated > 0) notes.push(`너무 긴 말 ${truncated}개는 2,000자까지만 올려요`);

  // 요청 전체 1MB: 최근 것부터 담다가 넘치면 멈춰요
  let bytes = 0;
  let from = limited.length;
  while (from > 0) {
    const size = encoder.encode(limited[from - 1].text).length + OVERHEAD_BYTES;
    if (bytes + size > MAX_BYTES) break;
    bytes += size;
    from -= 1;
  }
  if (from > 0) notes.push(`용량 한도(1MB) 때문에 더 오래된 말 ${from}개는 빼요`);
  const messages = limited.slice(from).map(({ date, text }) => ({ date, text }));

  if (messages.length === 0) return null;
  return {
    messages,
    periodStart: messages[0].date,
    periodEnd: messages[messages.length - 1].date,
    weekCount: new Set(messages.map((message) => mondayOf(message.date))).size,
    notes,
  };
}
