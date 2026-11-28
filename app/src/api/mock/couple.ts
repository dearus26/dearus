import { ApiError } from '@/api/errors';
import { NOT_HANDLED, validationFailed } from '@/api/mock/helpers';
import { uploadedDates } from '@/api/mock/uploads';
import type { components } from '@/api/schema';
import { addDays, daysBetween, isValidDateString, todayString } from '@/lib/date';

type User = components['schemas']['User'];
type Diary = components['schemas']['Diary'];
type Emotion = components['schemas']['Emotion'];
type PersonaScope = components['schemas']['PersonaScope'];
type CoupleState = components['schemas']['CoupleState'];
type CoupleDay = components['schemas']['CoupleDay'];
type CoupleComparison = components['schemas']['CoupleComparison'];

/**
 * 가짜 서버 중 "커플" 쪽이에요: 초대 코드 · 동의 · 함께 볼 날 · 두 시점 비교 · 철회(/couples/*).
 * 상대(도윤)는 앱 안에서 흉내 내요. 혼자서도 흐름을 끝까지 볼 수 있게 시간이 지나면 상대가 알아서 움직여요.
 *
 *  - 코드 만들기: 만든 지 10초 뒤에 상대가 입력해 연결돼요(GET /couples/me가 PENDING → CONNECTED로 바뀌어요).
 *  - 코드 입력하기: DU-7K4Q-2M9X는 연결돼요, DU-EXPR-2222는 만료된 코드, 내가 만든 코드는 못 쓰고, 그 밖의 코드는 없는 코드예요.
 *  - 연결되면 6초 뒤에 상대도 동의해요(내가 먼저 동의해도, 나중에 해도 같아요).
 *  - 대화를 끝내면 8초 뒤에 상대도 끝내고, 4초 뒤에 두 사람의 간극이 완성돼요.
 * 대화(POST /chats의 forCouple) 쪽은 chat.ts에 있고, 이 파일은 chat.ts를 모르게 두고 필요한 조회만 ctx.chats로 받아요.
 */

export const PARTNER_NICKNAME = '도윤';
const COUPLE_ID = '2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f';
export const DEMO_CODE = 'DU-7K4Q-2M9X';
const EXPIRED_CODE = 'DU-EXPR-2222';
const CODE_PATTERN = /^DU-[A-Z0-9]{4}-[A-Z0-9]{4}$/;
/** 헷갈리는 글자(0 · O · 1 · I)는 코드에 쓰지 않아요(명세) */
const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const CODE_TTL_MS = 24 * 60 * 60 * 1000;
const PARTNER_JOIN_MS = 10_000;
const PARTNER_CONSENT_MS = 6_000;
/** 내가 대화를 끝낸 뒤 상대가 끝내기까지 · 그 뒤 간극이 완성되기까지(가짜) */
const PARTNER_FINISH_MS = 8_000;
const GAP_MS = 4_000;
const MAX_RANGE_DAYS = 93;
const MAX_ANCHOR_DAYS = 365;

/** 상대가 쓴 일기(오늘 기준 며칠 전 → 그날 감정). 내 샘플 일기와 같은 날이 겹쳐야 "함께 볼 날"이 생겨요. */
const PARTNER_DIARIES: Record<number, Emotion[]> = {
  2: ['TIRED'],
  4: ['ANGRY'],
  7: ['EXCITED'],
  9: ['SAD'],
  21: ['CALM'],
  30: ['JOY'],
  41: ['CALM'],
  175: ['ANGRY'],
};

const NEGATIVE: Emotion[] = ['ANGRY', 'SAD', 'ANXIOUS'];

/** 두 사람의 대화가 끝난 뒤 보여주는 요약 · 간극(날짜와 상관없이 같은 문장이에요) */
const MY_PAST_SELF =
  '그날의 서연은 먼저 연락을 끊은 건 서운해서였는데, 그 마음을 설명하지 못해서 더 화가 났다고 했어요.';
const GAP_SUMMARY = '서운함은 침묵으로, 불안함은 다그침으로 전해졌어요.';

type Link = {
  status: 'NONE' | 'PENDING' | 'CONNECTED';
  role: 'INVITER' | 'INVITEE' | null;
  code: string | null;
  expiresAt: number;
  /** PENDING일 때 상대가 코드를 입력해 연결될 시각(가짜) */
  partnerJoinsAt: number | null;
  connectedAt: number | null;
  meConsented: boolean;
  /** 상대가 동의하는 시각(가짜). 아직 연결 전이면 null */
  partnerConsentAt: number | null;
};

const emptyLink = (): Link => ({
  status: 'NONE',
  role: null,
  code: null,
  expiresAt: 0,
  partnerJoinsAt: null,
  connectedAt: null,
  meConsented: false,
  partnerConsentAt: null,
});

let link: Link = emptyLink();

/** 회원 탈퇴로 가짜 서버를 처음 상태로 되돌릴 때 불러요. */
export function resetCoupleMock() {
  link = emptyLink();
}

/** 상대가 입력하기로 한 시각이 지났으면 연결해요. */
function settle() {
  const now = Date.now();
  if (link.status === 'PENDING' && link.partnerJoinsAt !== null && now >= link.partnerJoinsAt && now <= link.expiresAt) {
    link.status = 'CONNECTED';
    link.connectedAt = link.partnerJoinsAt;
    link.partnerConsentAt = link.partnerJoinsAt + PARTNER_CONSENT_MS;
    link.partnerJoinsAt = null;
  }
}

function partnerConsented(): boolean {
  return link.partnerConsentAt !== null && Date.now() >= link.partnerConsentAt;
}

/** 연결됨 + 양측 동의일 때만 열려요(명세 compareOpen). 대화(forCouple)와 같은 날 비교가 모두 이걸 확인해요. */
export function coupleCompareOpen(): boolean {
  settle();
  return link.status === 'CONNECTED' && link.meConsented && partnerConsented();
}

/** 연결된 커플의 id. 연결 전이면 null이에요(대화 세션의 coupleId에 써요). */
export function connectedCoupleId(): string | null {
  settle();
  return link.status === 'CONNECTED' ? COUPLE_ID : null;
}

export function coupleAccessDenied(): ApiError {
  return new ApiError(403, 'COUPLE_ACCESS_DENIED', '커플 연결과 두 사람의 동의가 있어야 쓸 수 있어요.');
}

/** 그날 상대가 쓴 일기의 감정 태그. 상대 일기가 없는 날이면 null이에요. */
export function partnerEmotionsOn(date: string): Emotion[] | null {
  return PARTNER_DIARIES[daysBetween(date, todayString())] ?? null;
}

function coupleState(user: User): CoupleState {
  settle();
  if (link.status === 'NONE') {
    return { status: 'NONE', coupleId: null, myRole: null, invite: null, me: null, partner: null, connectedAt: null, compareOpen: false };
  }
  if (link.status === 'PENDING') {
    const expired = Date.now() > link.expiresAt;
    return {
      status: expired ? 'EXPIRED' : 'PENDING',
      coupleId: COUPLE_ID,
      myRole: link.role,
      invite: !expired && link.code ? { code: link.code, expiresAt: new Date(link.expiresAt).toISOString() } : null,
      me: { nickname: user.nickname, consented: false },
      partner: null,
      connectedAt: null,
      compareOpen: false,
    };
  }
  const partnerDone = partnerConsented();
  return {
    status: 'CONNECTED',
    coupleId: COUPLE_ID,
    myRole: link.role,
    invite: null,
    me: { nickname: user.nickname, consented: link.meConsented },
    partner: { nickname: PARTNER_NICKNAME, consented: partnerDone },
    connectedAt: link.connectedAt === null ? null : new Date(link.connectedAt).toISOString(),
    compareOpen: link.meConsented && partnerDone,
  };
}

function newCode(): string {
  const pick = () =>
    Array.from({ length: 4 }, () => CODE_ALPHABET[Math.floor(Math.random() * CODE_ALPHABET.length)]).join('');
  return `DU-${pick()}-${pick()}`;
}

/** chat.ts가 알려 주는 "그날의 연인과 나눈 내 대화" 정보예요. */
export type CoupleChatInfo = {
  id: string;
  endedAt: string | null;
  /** 끝내기 뒤의 내 리포트 상태. 끝내기 전이면 null */
  report: 'NONE' | 'GENERATING' | 'READY' | null;
  /** 내 리포트의 "그날의 연인에게서 들은 이야기" */
  memorySummary: string | null;
};

export type CoupleChats = {
  /** 이 날(시점)의 내 커플 대화 중 가장 최근 것 */
  find: (anchorDate: string, scope: PersonaScope) => CoupleChatInfo | null;
  /** 철회하면 이 커플의 대화를 모두 지워요 */
  clear: () => void;
};

export type CoupleContext = { user: User; diaries: Diary[]; chats: CoupleChats };

function negative(emotions: Emotion[]): boolean {
  return emotions.some((emotion) => NEGATIVE.includes(emotion));
}

/** 명세의 추천 규칙: 한쪽이라도 화남 · 슬픔 · 불안이 있고, 두 사람의 감정이 다르거나 둘 다 화남이에요. */
function isRecommended(mine: Emotion[], theirs: Emotion[]): boolean {
  if (!negative(mine) && !negative(theirs)) return false;
  const same = mine.length === theirs.length && mine.every((emotion) => theirs.includes(emotion));
  return !same || (mine.includes('ANGRY') && theirs.includes('ANGRY'));
}

function checkDate(value: unknown, field: string): string {
  if (!isValidDateString(value)) throw validationFailed([{ field, reason: '날짜 형식이 올바르지 않아요' }]);
  return value;
}

/** /couples/* 요청을 처리해요. 이 파일이 맡은 경로가 아니면 NOT_HANDLED를 돌려줘요. */
export function handleCoupleRoute(
  method: string,
  path: string,
  query: Record<string, string>,
  body: unknown,
  ctx: CoupleContext,
): unknown {
  const { user, diaries, chats } = ctx;

  // GET /couples/me
  if (method === 'GET' && path === '/couples/me') return coupleState(user);

  // POST /couples/invite — 버튼을 누른 시점에 코드를 만들어요(24시간 유효, 아직 유효하면 같은 코드)
  if (method === 'POST' && path === '/couples/invite') {
    settle();
    if (link.status === 'CONNECTED') throw new ApiError(409, 'ALREADY_IN_COUPLE', '이미 연결된 커플이 있어요.');
    const now = Date.now();
    if (!(link.status === 'PENDING' && now <= link.expiresAt)) {
      link = {
        ...emptyLink(),
        status: 'PENDING',
        role: 'INVITER',
        code: newCode(),
        expiresAt: now + CODE_TTL_MS,
        partnerJoinsAt: now + PARTNER_JOIN_MS,
      };
    }
    return coupleState(user);
  }

  // POST /couples/invite/accept — 받은 코드로 연결해요
  if (method === 'POST' && path === '/couples/invite/accept') {
    const input = (body ?? {}) as { inviteCode?: unknown };
    const code = typeof input.inviteCode === 'string' ? input.inviteCode : '';
    if (!CODE_PATTERN.test(code)) {
      throw validationFailed([{ field: 'inviteCode', reason: '코드 형식이 올바르지 않아요' }]);
    }
    settle();
    if (link.status === 'CONNECTED') throw new ApiError(409, 'ALREADY_IN_COUPLE', '이미 연결된 커플이 있어요.');
    if (link.status === 'PENDING' && link.code === code) {
      throw new ApiError(400, 'COUPLE_OWN_CODE', '내가 만든 코드는 입력할 수 없어요.');
    }
    if (code === EXPIRED_CODE) {
      throw new ApiError(410, 'COUPLE_CODE_EXPIRED', '만료된 코드예요. 상대에게 새 코드를 받아 주세요.');
    }
    if (code !== DEMO_CODE) {
      throw new ApiError(404, 'COUPLE_CODE_INVALID', '없는 코드예요. 코드를 다시 확인해 주세요.');
    }
    // 내가 만들어 둔 코드가 있었으면 취소되고, 이 코드로 연결돼요
    const now = Date.now();
    link = { ...emptyLink(), status: 'CONNECTED', role: 'INVITEE', connectedAt: now, partnerConsentAt: now + PARTNER_CONSENT_MS };
    return coupleState(user);
  }

  // POST /couples/consent — 내 동의
  if (method === 'POST' && path === '/couples/consent') {
    const input = (body ?? {}) as { agreed?: unknown };
    if (input.agreed !== true) throw validationFailed([{ field: 'agreed', reason: '동의해야 계속할 수 있어요' }]);
    settle();
    if (link.status !== 'CONNECTED') {
      throw new ApiError(409, 'COUPLE_NOT_CONNECTED', '연결된 커플이 없어요. 먼저 초대 코드로 연결해 주세요.');
    }
    link.meConsented = true;
    return coupleState(user);
  }

  // GET /couples/days?from&to — 두 사람의 기록이 모두 있는 날(내 쪽은 일기 또는 그날 올린 메신저, 서버 2026.11.28과 같아요)
  if (method === 'GET' && path === '/couples/days') {
    const from = checkDate(query.from, 'from');
    const to = checkDate(query.to, 'to');
    if (from > to) throw validationFailed([{ field: 'from', reason: '시작 날짜가 끝 날짜보다 늦을 수 없어요' }]);
    if (daysBetween(from, to) + 1 > MAX_RANGE_DAYS) {
      throw validationFailed([{ field: 'to', reason: `한 번에 ${MAX_RANGE_DAYS}일까지만 볼 수 있어요` }]);
    }
    if (!coupleCompareOpen()) throw coupleAccessDenied();

    const today = todayString();
    const earliest = addDays(today, -MAX_ANCHOR_DAYS);
    const items: CoupleDay[] = [];
    // 가짜 연인은 일기만 있어요. 내 쪽은 일기가 없어도 그날 메신저를 올렸으면 기록이 있는 날이고, 감정은 비어 있어요
    const myDays = new Map<string, Emotion[]>(diaries.map((diary) => [diary.date, diary.emotions]));
    for (const date of uploadedDates()) if (!myDays.has(date)) myDays.set(date, []);
    for (const [date, mine] of myDays) {
      if (date < from || date > to || date > today || date < earliest) continue;
      const theirs = partnerEmotionsOn(date);
      if (!theirs) continue;
      items.push({ date, myEmotions: mine, partnerEmotions: theirs, recommended: isRecommended(mine, theirs) });
    }
    items.sort((a, b) => a.date.localeCompare(b.date));
    return { items };
  }

  // GET /couples/comparisons?anchorDate&scope — 같은 날 두 사람의 기억 비교
  if (method === 'GET' && path === '/couples/comparisons') {
    const anchorDate = checkDate(query.anchorDate, 'anchorDate');
    const scopeText = query.scope ?? 'DAY';
    if (scopeText !== 'DAY' && scopeText !== 'AROUND') {
      throw validationFailed([{ field: 'scope', reason: 'DAY 또는 AROUND여야 해요' }]);
    }
    const scope: PersonaScope = scopeText === 'AROUND' ? 'AROUND' : 'DAY';
    if (!coupleCompareOpen()) throw coupleAccessDenied();

    const info = chats.find(anchorDate, scope);
    const base = { anchorDate, scope, partnerNickname: PARTNER_NICKNAME };
    // 아직 시작하지 않았거나(내 말이 2개 미만이라 리포트가 없는 대화도 포함해요) 대화 중이에요
    if (!info || info.report === 'NONE') {
      const comparison: CoupleComparison = { ...base, status: 'NOT_STARTED', chatId: null, partnerPastSelf: null, myPastSelf: null, gapSummary: null };
      return comparison;
    }
    const mine = { ...base, chatId: info.id };
    if (!info.endedAt) {
      const comparison: CoupleComparison = { ...mine, status: 'IN_PROGRESS', partnerPastSelf: null, myPastSelf: null, gapSummary: null };
      return comparison;
    }
    // 내 리포트를 만드는 중이에요
    if (info.report !== 'READY' || !info.memorySummary) {
      const comparison: CoupleComparison = { ...mine, status: 'GENERATING', partnerPastSelf: null, myPastSelf: null, gapSummary: null };
      return comparison;
    }
    const partnerPastSelf = { memorySummary: info.memorySummary };
    const sinceEnd = Date.now() - new Date(info.endedAt).getTime();
    if (sinceEnd < PARTNER_FINISH_MS) {
      // 나는 끝냈고 상대를 기다려요. 내가 들은 이야기는 이미 볼 수 있어요
      const comparison: CoupleComparison = { ...mine, status: 'WAITING_PARTNER', partnerPastSelf, myPastSelf: null, gapSummary: null };
      return comparison;
    }
    const myPastSelf = { memorySummary: MY_PAST_SELF };
    if (sinceEnd < PARTNER_FINISH_MS + GAP_MS) {
      const comparison: CoupleComparison = { ...mine, status: 'GENERATING', partnerPastSelf, myPastSelf, gapSummary: null };
      return comparison;
    }
    const comparison: CoupleComparison = { ...mine, status: 'READY', partnerPastSelf, myPastSelf, gapSummary: GAP_SUMMARY };
    return comparison;
  }

  // POST /couples/revoke — 동의 철회 · 연결 해제(이 커플의 대화와 리포트는 모두 지워져요)
  if (method === 'POST' && path === '/couples/revoke') {
    chats.clear();
    link = emptyLink();
    return coupleState(user);
  }

  return NOT_HANDLED;
}
