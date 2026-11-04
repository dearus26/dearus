import { ApiError, UNAUTHORIZED_MESSAGE } from '@/api/errors';
import type { components } from '@/api/schema';
import {
  addDays,
  addMonths,
  daysBetween,
  daysInMonth,
  isValidDateString,
  parseDateString,
  toDateString,
  todayString,
  weekdayOf,
} from '@/lib/date';

type User = components['schemas']['User'];
type AuthResponse = components['schemas']['AuthResponse'];
type Diary = components['schemas']['Diary'];
type Emotion = components['schemas']['Emotion'];
type PersonaWindow = components['schemas']['PersonaWindow'];
type PersonaScope = components['schemas']['PersonaScope'];
type ShortcutKey = components['schemas']['ShortcutKey'];
type RecordLevel = components['schemas']['RecordLevel'];
type ChatSession = components['schemas']['ChatSession'];
type ChatListItem = components['schemas']['ChatListItem'];

/**
 * 서버 없이 앱을 만들 때 쓰는 가짜 서버예요(EXPO_PUBLIC_API_MODE=mock).
 * 요청 · 응답 모양은 server/openapi.yaml을 따라요. 화면은 이걸 몰라요 — api/ 함수만 불러요.
 * 메모리에만 있어서 앱을 완전히 다시 켜면 처음 상태로 돌아가요.
 */

const MOCK_TOKEN = 'mock-access-token';
const DELAY_MS = 350;
const RETENTION_DAYS = 30;
const MAX_RANGE_DAYS = 93;
const MAX_CONTENT = 5000;
const AROUND_DAYS = 14;
const MAX_ANCHOR_DAYS = 365;
const CHAT_LIST_LIMIT = 50;
const SHORTCUT_KEYS: ShortcutKey[] = ['ONE_MONTH', 'SIX_MONTHS', 'ONE_YEAR'];
/** 시점 분석에 걸리는 시간(가짜). 실제 서버는 전후 2주가 약 14초, 하루가 약 9초예요. */
const PREPARE_MS: Record<PersonaScope, number> = { AROUND: 7000, DAY: 4500 };
const MOCK_GREETING = '어, 왔어? 오랜만이다 ㅋㅋ';

let user: User | null = null;
let diaries: Diary[] | null = null;
let diarySeq = 0;

const wait = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

function newUser(provider: User['provider']): User {
  return {
    id: '00000000-0000-4000-8000-000000000001',
    provider,
    nickname: '서연',
    aiConsentedAt: null,
    aiRevokedAt: null,
    aiDataDeleteAt: null,
    aiConsentActive: false,
    needsConsent: true,
    notificationsOptIn: false,
    createdAt: new Date().toISOString(),
  };
}

function validationFailed(fields: { field: string; reason: string }[]): ApiError {
  return new ApiError(400, 'VALIDATION_FAILED', '입력한 내용을 다시 확인해 주세요.', { fields });
}

function notFound(): ApiError {
  return new ApiError(404, 'NOT_FOUND', '찾을 수 없어요.');
}

/** '/diaries?from=a&to=b' → 경로와 쿼리로 나눠요. */
function splitPath(fullPath: string): { path: string; query: Record<string, string> } {
  const [path, queryString = ''] = fullPath.split('?');
  const query: Record<string, string> = {};
  for (const pair of queryString.split('&')) {
    if (!pair) continue;
    const [key, value = ''] = pair.split('=');
    query[decodeURIComponent(key)] = decodeURIComponent(value);
  }
  return { path, query };
}

/** 캘린더가 비어 보이지 않게, 오늘 기준 최근 며칠의 샘플 일기를 깔아 둬요(가짜 데이터). */
function seedDiaries(): Diary[] {
  const today = todayString();
  const samples: { ago: number; emotions: Emotion[]; content: string }[] = [
    { ago: 1, emotions: ['JOY'], content: '오늘 팀 회의에서 내 아이디어가 채택돼서 완전 기분 좋았어 ㅋㅋ 집 오는 길에 좋아하는 노래 크게 틀고 왔다' },
    {
      ago: 2,
      emotions: ['CALM', 'TIRED'],
      content:
        '오늘은 팀원들이랑 발표 준비했는데 하루 종일 앉아 있었더니 진짜 피곤하다. 그래도 마음은 평온해.\n\n아침엔 스토리보드 화면을 하나씩 같이 넘겨 보면서 이야기했는데, 생각보다 의견이 잘 맞아서 놀랐어 ㅋㅋ 점심 먹고 나서는 로그인 화면이랑 홈 화면을 다시 손봤고, 오후 내내 캘린더 모양을 고민했어.\n\n근데 돌아보면 요즘 나는 하루 종일 뭔가를 만들고 있는데도 이상하게 마음이 편해. 예전엔 할 일이 많으면 불안했는데 지금은 해 보면 된다는 생각이 들어서 그런가 봐.\n\n내일은 일찍 자고, 아침에 천천히 커피 마시면서 시작해야지. 오늘 하루 고생했다.',
    },
    { ago: 4, emotions: ['ANGRY'], content: '그날 진짜 많이 속상했어. 말을 꺼내고 싶었는데 못 했다. 괜히 더 답답하네' },
    { ago: 5, emotions: ['SAD'], content: '비 와서 그런가 괜히 가라앉는 하루. 예전 사진 보다가 좀 울컥했다' },
    { ago: 7, emotions: ['EXCITED'], content: '내일 기다리던 약속이 있어서 설레서 잠이 안 와 ㅋㅋ' },
    { ago: 9, emotions: ['ANXIOUS'], content: '진로 생각하면 막막해. 다들 앞서가는 것 같고 나만 제자리 같아서 불안해' },
    // 시점 고르기의 기록 수준이 다양하게 보이도록 한 달 전(충분) · 6개월 전(부족) 무렵 일기도 깔아 둬요. 1년 전은 비워서 "기록 없음"을 볼 수 있어요.
    { ago: 18, emotions: ['TIRED'], content: '요즘 과제가 너무 많아서 잠을 못 잤어. 그래도 하나씩 끝내는 중' },
    { ago: 21, emotions: ['CALM'], content: '오랜만에 아무 약속 없는 주말. 카페에서 책 읽다 왔는데 마음이 느긋해' },
    { ago: 26, emotions: ['ANXIOUS'], content: '진로 상담 받고 나니까 오히려 더 막막해졌어. 뭘 좋아하는지도 모르겠다' },
    { ago: 30, emotions: ['JOY', 'EXCITED'], content: '팀 프로젝트 주제가 정해졌다! 다 같이 아이디어 내는 게 생각보다 너무 재밌었어 ㅋㅋ' },
    { ago: 33, emotions: ['SAD'], content: '친구랑 사소한 일로 어색해졌어. 먼저 연락할까 말까 계속 고민 중' },
    { ago: 41, emotions: ['CALM'], content: '비 오는 날 창가에 앉아서 노래 들었다. 이런 하루도 나쁘지 않네' },
    { ago: 45, emotions: ['TIRED', 'ANXIOUS'], content: '개강하고 한 달. 다들 잘 해내는 것 같은데 나만 허덕이는 기분이야' },
    { ago: 175, emotions: ['ANGRY'], content: '발표 준비 안 도와주고 빠진 팀원 때문에 진짜 화났다. 말은 못 했지만' },
  ];
  return samples.map(({ ago, emotions, content }) => {
    const date = addDays(today, -ago);
    const stamp = `${date}T21:30:00+09:00`;
    return { id: `mock-diary-${++diarySeq}`, date, content, emotions, createdAt: stamp, updatedAt: stamp };
  });
}

function store(): Diary[] {
  diaries = diaries ?? seedDiaries();
  return diaries;
}

function sortedEmotions(emotions: Emotion[] | undefined): Emotion[] {
  return Array.from(new Set(emotions ?? []));
}

// ───── 과거의 나: 시점 고르기 · 대화 세션 ─────

/** 오늘 기준 몇 개월 전 같은 날(그 달에 없는 날이면 말일) */
function monthsAgo(date: string, count: number): string {
  const { year, month, day } = parseDateString(date);
  const target = addMonths(year, month, -count);
  return toDateString(target.year, target.month, Math.min(day, daysInMonth(target.year, target.month)));
}

/** 바로가기 기준일: 1개월 · 6개월 · 1년 전이 속한 주의 월요일. 1년 전이 365일을 넘으면 다음 월요일이에요. */
function shortcutAnchor(key: ShortcutKey, today: string): string {
  const months = key === 'ONE_MONTH' ? 1 : key === 'SIX_MONTHS' ? 6 : 12;
  const ago = monthsAgo(today, months);
  const monday = addDays(ago, -((weekdayOf(ago) + 6) % 7));
  return daysBetween(monday, today) > MAX_ANCHOR_DAYS ? addDays(monday, 7) : monday;
}

/** 기록 수준 초안 기준(명세 RecordLevel 표)대로예요. */
function recordLevel(scope: PersonaScope, diaryCount: number, messengerWeeks: number): RecordLevel {
  if (scope === 'DAY') return diaryCount > 0 ? 'ENOUGH' : 'NONE';
  if (diaryCount >= 10) return 'RICH';
  if (diaryCount >= 5 || messengerWeeks >= 1) return 'ENOUGH';
  return diaryCount >= 1 ? 'LOW' : 'NONE';
}

function personaWindow(anchorDate: string, scope: PersonaScope): PersonaWindow {
  const today = todayString();
  const windowStart = scope === 'DAY' ? anchorDate : addDays(anchorDate, -AROUND_DAYS);
  const after = addDays(anchorDate, AROUND_DAYS);
  const windowEnd = scope === 'DAY' ? anchorDate : after > today ? today : after;
  const inRange = store().filter((diary) => diary.date >= windowStart && diary.date <= windowEnd);
  const messengerWeeks = 0; // 메신저 올리기는 아직 가짜 서버에 없어요
  return {
    anchorDate,
    scope,
    windowStart,
    windowEnd,
    diaryCount: inRange.length,
    messengerWeeks,
    emotions: scope === 'DAY' ? sortedEmotions(inRange[0]?.emotions) : undefined,
    level: recordLevel(scope, inRange.length, messengerWeeks),
  };
}

/** 그 범위의 일기가 바뀌었는지 알아보는 표(저장된 분석을 다시 쓸 수 있는지 볼 때 써요) */
function recordsFingerprint(range: PersonaWindow): string {
  return store()
    .filter((diary) => diary.date >= range.windowStart && diary.date <= range.windowEnd)
    .map((diary) => `${diary.id}:${diary.updatedAt}`)
    .join(',');
}

/** 앵커 날짜는 오늘로부터 365일 전 ~ 오늘만 고를 수 있어요. */
function checkAnchor(value: unknown, field: string): string {
  if (!isValidDateString(value)) throw validationFailed([{ field, reason: '날짜 형식이 올바르지 않아요' }]);
  if (value > todayString() || daysBetween(value, todayString()) > MAX_ANCHOR_DAYS) {
    throw new ApiError(400, 'ANCHOR_OUT_OF_RANGE', '고를 수 없는 날짜예요. 오늘부터 1년 전까지 고를 수 있어요.');
  }
  return value;
}

type MockChat = {
  id: string;
  shortcutKey: ShortcutKey | null;
  /** 세션을 만들 때의 시점 · 기록량 */
  range: PersonaWindow;
  startedAt: string;
  lastMessageAt: string;
  lastMessagePreview: string | null;
  /** 분석 중인 동안의 시작 · 끝 시각(ms). 없으면 준비가 끝난 세션이에요. */
  preparing: { from: number; until: number } | null;
};

let chats: MockChat[] | null = null;
let chatSeq = 0;
/** 분석을 저장해 둔 시점(`범위|기준일` → 그때의 기록 표). 기록이 안 바뀌었으면 다시 분석하지 않고 바로 만나요. */
const analyses = new Map<string, string>();

const analysisKey = (scope: PersonaScope, anchorDate: string) => `${scope}|${anchorDate}`;

/** 목록이 비어 보이지 않게, 스토리보드처럼 지난 대화 세 개를 깔아 둬요(가짜 데이터). */
function seedChats(): MockChat[] {
  const today = todayString();
  const make = (
    scope: PersonaScope,
    shortcutKey: ShortcutKey | null,
    anchorDate: string,
    daysAgo: number,
    time: string,
    preview: string,
  ): MockChat => {
    const range = personaWindow(anchorDate, scope);
    analyses.set(analysisKey(scope, anchorDate), recordsFingerprint(range));
    const stamp = `${addDays(today, -daysAgo)}T${time}:00+09:00`;
    return {
      id: `mock-chat-${++chatSeq}`,
      shortcutKey,
      range,
      startedAt: stamp,
      lastMessageAt: stamp,
      lastMessagePreview: preview,
      preparing: null,
    };
  };
  return [
    make('AROUND', 'ONE_MONTH', shortcutAnchor('ONE_MONTH', today), 1, '21:10', '지금은 어느 정도 방향 잡았어. 너무 걱정 마'),
    make('DAY', null, addDays(today, -4), 3, '20:30', '그날 진짜 많이 속상했어, 근데 말을 못 꺼내겠더라'),
    make('AROUND', 'ONE_YEAR', shortcutAnchor('ONE_YEAR', today), 7, '22:00', '요즘은 뭐 하고 지내? 나는 거의 똑같아'),
  ];
}

function chatStore(): MockChat[] {
  chats = chats ?? seedChats();
  return chats;
}

/** 분석이 끝났으면 준비 중 표시를 걷고 첫 인사를 마지막 메시지로 두어요. 아직 분석 중이면 true */
function isPreparing(chat: MockChat): boolean {
  if (chat.preparing && Date.now() >= chat.preparing.until) {
    chat.preparing = null;
    chat.lastMessagePreview = MOCK_GREETING;
  }
  return chat.preparing !== null;
}

function preparingStep(preparing: { from: number; until: number }): NonNullable<ChatSession['preparingStep']> {
  const progress = (Date.now() - preparing.from) / (preparing.until - preparing.from);
  return progress < 0.2 ? 'COLLECT' : progress < 0.45 ? 'MASK' : progress < 0.8 ? 'ANALYZE' : 'BUILD';
}

function chatSession(chat: MockChat): ChatSession {
  const preparing = isPreparing(chat);
  return {
    id: chat.id,
    status: preparing ? 'PREPARING' : 'READY',
    preparingStep: chat.preparing ? preparingStep(chat.preparing) : null,
    failureCode: null,
    mode: 'PERSONAL',
    coupleId: null,
    scope: chat.range.scope,
    shortcutKey: chat.shortcutKey,
    anchorDate: chat.range.anchorDate,
    windowStart: chat.range.windowStart,
    windowEnd: chat.range.windowEnd,
    level: chat.range.level,
    startedAt: chat.startedAt,
    endedAt: null,
  };
}

function chatListItem(chat: MockChat): ChatListItem {
  const session = chatSession(chat);
  return {
    id: chat.id,
    scope: session.scope,
    shortcutKey: chat.shortcutKey,
    anchorDate: session.anchorDate,
    emotions: chat.range.emotions ?? [],
    level: session.level,
    status: session.status,
    lastMessagePreview: chat.lastMessagePreview,
    lastMessageAt: chat.lastMessageAt,
    startedAt: chat.startedAt,
    endedAt: null,
  };
}

export async function mockRequest<T>(
  method: string,
  fullPath: string,
  body: unknown,
  token: string | null,
): Promise<T> {
  await wait(DELAY_MS);
  const { path, query } = splitPath(fullPath);

  // POST /auth/{provider} — 로그인
  const login = /^\/auth\/(kakao|google|apple|dev)$/.exec(path);
  if (method === 'POST' && login) {
    const isNewUser = user === null;
    user = user ?? newUser(login[1] as User['provider']);
    const response: AuthResponse = {
      accessToken: MOCK_TOKEN,
      tokenType: 'Bearer',
      expiresAt: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString(),
      isNewUser,
      user,
    };
    return response as T;
  }

  // 여기부터는 로그인이 필요해요
  if (token !== MOCK_TOKEN) {
    throw new ApiError(401, 'UNAUTHORIZED', UNAUTHORIZED_MESSAGE);
  }
  // 앱을 껐다 켜서 가짜 서버가 초기화됐어도 저장된 토큰으로 이어서 쓰게 해요
  user = user ?? newUser('dev');

  // GET /me
  if (method === 'GET' && path === '/me') {
    return user as T;
  }

  // PUT /me/consent — 서비스 이용 동의
  if (method === 'PUT' && path === '/me/consent') {
    const input = (body ?? {}) as Partial<components['schemas']['UserConsentRequest']>;
    const missing = (['terms', 'privacy', 'aiAnalysis'] as const)
      .filter((key) => input[key] !== true)
      .map((field) => ({ field, reason: '꼭 동의해야 해요' }));
    if (missing.length > 0) throw validationFailed(missing);

    // 이미 유효한 동의에 다시 불러도 시각은 그대로 두고 알림 설정만 바꿔요
    if (!user.aiConsentActive) {
      user = { ...user, aiConsentedAt: new Date().toISOString(), aiRevokedAt: null, aiDataDeleteAt: null };
    }
    user = {
      ...user,
      aiConsentActive: true,
      needsConsent: false,
      notificationsOptIn: input.notifications ?? user.notificationsOptIn,
    };
    return user as T;
  }

  // DELETE /me/consent — AI 분석 동의 철회 (분석 데이터는 30일 뒤 삭제)
  if (method === 'DELETE' && path === '/me/consent') {
    if (user.aiConsentActive) {
      const now = new Date();
      user = {
        ...user,
        aiConsentActive: false,
        aiRevokedAt: now.toISOString(),
        aiDataDeleteAt: new Date(now.getTime() + RETENTION_DAYS * 24 * 60 * 60 * 1000).toISOString(),
      };
    }
    return user as T;
  }

  // DELETE /me — 회원 탈퇴
  if (method === 'DELETE' && path === '/me') {
    user = null;
    diaries = null;
    chats = null;
    analyses.clear();
    return undefined as T;
  }

  // GET /diaries?from&to — 기간별 일기 (최대 93일)
  if (method === 'GET' && path === '/diaries') {
    const { from, to } = query;
    const problems: { field: string; reason: string }[] = [];
    if (!isValidDateString(from)) problems.push({ field: 'from', reason: '날짜 형식이 올바르지 않아요' });
    if (!isValidDateString(to)) problems.push({ field: 'to', reason: '날짜 형식이 올바르지 않아요' });
    if (problems.length > 0) throw validationFailed(problems);
    if (from > to) throw validationFailed([{ field: 'from', reason: '시작 날짜가 끝 날짜보다 늦을 수 없어요' }]);
    if (daysBetween(from, to) + 1 > MAX_RANGE_DAYS) {
      throw validationFailed([{ field: 'to', reason: `한 번에 ${MAX_RANGE_DAYS}일까지만 볼 수 있어요` }]);
    }
    const items = store()
      .filter((diary) => diary.date >= from && diary.date <= to)
      .sort((a, b) => a.date.localeCompare(b.date));
    return { items } as T;
  }

  // POST /diaries — 일기 쓰기 (하루에 한 건)
  if (method === 'POST' && path === '/diaries') {
    const input = (body ?? {}) as Partial<components['schemas']['DiaryCreate']>;
    const problems: { field: string; reason: string }[] = [];
    if (!isValidDateString(input.date)) problems.push({ field: 'date', reason: '날짜 형식이 올바르지 않아요' });
    else if (input.date > todayString()) problems.push({ field: 'date', reason: '미래 날짜의 일기는 쓸 수 없어요' });
    if (!input.content || input.content.length < 1) problems.push({ field: 'content', reason: '비어 있을 수 없어요' });
    else if (input.content.length > MAX_CONTENT) problems.push({ field: 'content', reason: `${MAX_CONTENT}자까지 쓸 수 있어요` });
    if (problems.length > 0) throw validationFailed(problems);

    const existing = store().find((diary) => diary.date === input.date);
    if (existing) {
      throw new ApiError(409, 'DIARY_ALREADY_EXISTS', '이 날짜에 쓴 일기가 이미 있어요.', { diaryId: existing.id });
    }
    const now = new Date().toISOString();
    const diary: Diary = {
      id: `mock-diary-${++diarySeq}`,
      date: input.date as string,
      content: input.content as string,
      emotions: sortedEmotions(input.emotions),
      createdAt: now,
      updatedAt: now,
    };
    store().push(diary);
    return diary as T;
  }

  // PUT /diaries/{id} · DELETE /diaries/{id}
  const diaryMatch = /^\/diaries\/([^/]+)$/.exec(path);
  if (diaryMatch) {
    const index = store().findIndex((diary) => diary.id === diaryMatch[1]);
    if (index < 0) throw notFound();

    if (method === 'PUT') {
      const input = (body ?? {}) as Partial<components['schemas']['DiaryUpdate']>;
      if (!input.content || input.content.length < 1) {
        throw validationFailed([{ field: 'content', reason: '비어 있을 수 없어요' }]);
      }
      if (input.content.length > MAX_CONTENT) {
        throw validationFailed([{ field: 'content', reason: `${MAX_CONTENT}자까지 쓸 수 있어요` }]);
      }
      const updated: Diary = {
        ...store()[index],
        content: input.content,
        emotions: sortedEmotions(input.emotions),
        updatedAt: new Date().toISOString(),
      };
      store()[index] = updated;
      return updated as T;
    }

    if (method === 'DELETE') {
      store().splice(index, 1);
      return undefined as T;
    }
  }

  // GET /personas/shortcuts — 바로가기 3개 (전후 2주)
  if (method === 'GET' && path === '/personas/shortcuts') {
    const today = todayString();
    const items = SHORTCUT_KEYS.map((key) => ({ ...personaWindow(shortcutAnchor(key, today), 'AROUND'), key }));
    return { items } as T;
  }

  // GET /personas/preview?anchor&scope — 고른 날짜의 기록량
  if (method === 'GET' && path === '/personas/preview') {
    const anchor = checkAnchor(query.anchor, 'anchor');
    const scope = query.scope ?? 'DAY';
    if (scope !== 'DAY' && scope !== 'AROUND') {
      throw validationFailed([{ field: 'scope', reason: 'DAY 또는 AROUND여야 해요' }]);
    }
    return personaWindow(anchor, scope) as T;
  }

  // GET /chats — 과거의 나 목록 (최근 대화 순)
  if (method === 'GET' && path === '/chats') {
    const items = chatStore()
      .map(chatListItem)
      .sort((a, b) => b.lastMessageAt.localeCompare(a.lastMessageAt))
      .slice(0, CHAT_LIST_LIMIT);
    return { items } as T;
  }

  // POST /chats — 과거의 나와 대화 시작 (같은 시점의 분석이 있고 기록이 안 바뀌었으면 바로 READY)
  if (method === 'POST' && path === '/chats') {
    const input = (body ?? {}) as Partial<components['schemas']['ChatCreate']>;
    const scope = input.scope;
    if (scope !== 'DAY' && scope !== 'AROUND') {
      throw validationFailed([{ field: 'scope', reason: 'DAY 또는 AROUND여야 해요' }]);
    }
    if (input.shortcutKey && scope !== 'AROUND') {
      throw validationFailed([{ field: 'shortcutKey', reason: '바로가기는 전후 2주(AROUND)일 때만 보낼 수 있어요' }]);
    }
    if (!user.aiConsentActive) {
      throw new ApiError(403, 'AI_CONSENT_REQUIRED', 'AI 분석에 동의해야 쓸 수 있어요.');
    }
    if (input.forCouple) {
      throw new ApiError(403, 'COUPLE_ACCESS_DENIED', '커플 모드는 아직 쓸 수 없어요.');
    }

    const range = personaWindow(checkAnchor(input.anchorDate, 'anchorDate'), scope);
    const key = analysisKey(scope, range.anchorDate);
    const fingerprint = recordsFingerprint(range);
    // 기록이 하나도 없으면(NONE) 분석 없이 바로 기본 말투로 대화해요
    const needsAnalysis = range.level !== 'NONE' && analyses.get(key) !== fingerprint;
    if (needsAnalysis) analyses.set(key, fingerprint);

    const now = Date.now();
    const stamp = new Date(now).toISOString();
    const chat: MockChat = {
      id: `mock-chat-${++chatSeq}`,
      shortcutKey: input.shortcutKey ?? null,
      range,
      startedAt: stamp,
      lastMessageAt: stamp,
      lastMessagePreview: needsAnalysis ? null : MOCK_GREETING,
      preparing: needsAnalysis ? { from: now, until: now + PREPARE_MS[scope] } : null,
    };
    chatStore().push(chat);
    return chatSession(chat) as T;
  }

  // GET /chats/{id} — 세션 상태 (PREPARING이면 앱이 1~2초 간격으로 조회해요)
  const chatMatch = /^\/chats\/([^/]+)$/.exec(path);
  if (method === 'GET' && chatMatch) {
    const chat = chatStore().find((candidate) => candidate.id === chatMatch[1]);
    if (!chat) throw notFound();
    return chatSession(chat) as T;
  }

  throw notFound();
}
