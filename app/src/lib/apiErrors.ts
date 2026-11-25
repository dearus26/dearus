import { router } from 'expo-router';

import { ApiError, errorMessage } from '@/api/errors';
import { addDays, dateStringOf, formatMonthDay } from '@/lib/date';
import { confirm, notify } from '@/lib/dialog';

/** AI 분석 동의를 다시 하러 가는 화면(설정 → AI 분석 동의) */
export const AI_CONSENT_ROUTE = '/settings/ai-consent';

type Notice = {
  title: string;
  message: string;
  /** true면 안내 뒤에 "AI 분석 동의" 화면으로 갈 수 있게 해요 */
  goToAiConsent?: boolean;
};

/** "내일 0시", "11월 30일 0시" — 한국 시간 자정이 폰의 시간대로는 다른 시각일 수 있어서 폰 현지 시각으로 풀어 써요. */
export function describeResetTime(resetAt: unknown, now: Date = new Date()): string | null {
  if (typeof resetAt !== 'string') return null;
  const at = new Date(resetAt);
  if (Number.isNaN(at.getTime())) return null;
  const today = dateStringOf(now);
  const day = dateStringOf(at);
  const dayText = day === today ? '오늘' : day === addDays(today, 1) ? '내일' : formatMonthDay(day);
  return `${dayText} ${at.getHours()}시${at.getMinutes() > 0 ? ` ${at.getMinutes()}분` : ''}`;
}

/**
 * 서버가 돌려주는 오류 코드 중 화면에서 따로 안내하는 것들이에요(명세 공통 오류).
 *  - 403 AI_CONSENT_REQUIRED: AI 분석 동의가 없거나 철회했어요 → 다시 동의하러 가는 길을 열어 줘요.
 *  - 429 AI_DAILY_LIMIT: 하루 사용량을 다 썼어요(`details.resetAt`에 다시 쓸 수 있는 시각).
 *  - 429 CHAT_MESSAGE_LIMIT: 이 대화에서 보낼 수 있는 내 말을 다 썼어요 → 대화를 끝내고 새로 시작해요.
 *  - 429 RATE_LIMITED: 짧은 시간에 요청이 너무 많아요 → 잠시 뒤에 다시 해요.
 * 그 밖의 코드는 null이고, 이때는 서버가 준 문장을 그대로 보여줘요.
 */
export function noticeFor(error: unknown): Notice | null {
  if (!(error instanceof ApiError)) return null;
  switch (error.code) {
    case 'AI_CONSENT_REQUIRED':
      return {
        title: 'AI 분석 동의가 필요해요',
        message: 'AI를 쓰는 기능은 AI 분석에 동의해야 쓸 수 있어요. 일기는 동의 없이도 쓸 수 있어요.',
        goToAiConsent: true,
      };
    case 'AI_DAILY_LIMIT': {
      const reset = describeResetTime(error.details?.resetAt);
      return {
        title: '오늘은 여기까지예요',
        message: reset
          ? `오늘 쓸 수 있는 AI 사용량을 다 썼어요. ${reset}에 다시 쓸 수 있어요.`
          : '오늘 쓸 수 있는 AI 사용량을 다 썼어요. 내일 다시 써 주세요.',
      };
    }
    case 'CHAT_MESSAGE_LIMIT':
      return {
        title: '이 대화는 여기까지예요',
        message: '한 대화에서 보낼 수 있는 말을 모두 썼어요. 대화를 끝내고 새로 시작해 주세요. 끝낸 대화는 리포트로 남아요.',
      };
    case 'RATE_LIMITED':
      return {
        title: '잠시만 기다려 주세요',
        message: '요청이 너무 잦아요. 잠시 뒤에 다시 시도해 주세요.',
      };
    default:
      return null;
  }
}

/**
 * 서버를 부르다 실패했을 때 알려 주는 창이에요. 화면은 `notify(제목, errorMessage(error))` 대신 이걸 써요.
 * 동의 · 사용 상한처럼 사용자가 할 일이 정해진 코드는 그에 맞는 제목 · 안내로 바꾸고(noticeFor),
 * 나머지는 `title`과 서버 문장을 그대로 보여줘요.
 */
export function notifyApiError(title: string, error: unknown): void {
  const notice = noticeFor(error);
  if (!notice) {
    notify(title, errorMessage(error));
    return;
  }
  if (!notice.goToAiConsent) {
    notify(notice.title, notice.message);
    return;
  }
  void confirm({
    title: notice.title,
    message: `${notice.message}\n\n동의 관리로 갈까요?`,
    confirmLabel: '동의 관리로 가기',
    cancelLabel: '닫기',
  }).then((ok) => {
    if (ok) router.push(AI_CONSENT_ROUTE);
  });
}
