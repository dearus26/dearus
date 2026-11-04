import { ApiError } from '@/api/errors';
import { notFound, sortedEmotions, validationFailed, wait } from '@/api/mock/helpers';
import type { components } from '@/api/schema';
import type { SseEvent } from '@/api/sse';
import {
  addDays,
  addMonths,
  dateStringOf,
  daysBetween,
  daysInMonth,
  isValidDateString,
  parseDateString,
  toDateString,
  todayString,
  weekdayOf,
} from '@/lib/date';

type User = components['schemas']['User'];
type Diary = components['schemas']['Diary'];
type PersonaWindow = components['schemas']['PersonaWindow'];
type PersonaScope = components['schemas']['PersonaScope'];
type ShortcutKey = components['schemas']['ShortcutKey'];
type RecordLevel = components['schemas']['RecordLevel'];
type ChatSession = components['schemas']['ChatSession'];
type ChatListItem = components['schemas']['ChatListItem'];
type ChatMessage = components['schemas']['ChatMessage'];
type Report = components['schemas']['Report'];
type SafetyNotice = components['schemas']['SafetyNotice'];

/**
 * 가짜 서버 중 "과거의 나" 쪽이에요: 시점 고르기(/personas) · 대화 세션 · 메시지(SSE) · 끝내기 · 리포트 · 오늘 대화 시간.
 * 동작은 실제 서버(server/)를 따라해요(분석 재사용, 준비 단계, 답변 도중 끊겨도 저장, 5분 상한 대화 시간 등).
 */

/** index.ts가 요청마다 넘겨 주는 지금 사용자와 일기 목록이에요. */
export type MockContext = { user: User; diaries: Diary[] };

/** handleChatRoute가 이 경로를 처리하지 않았다는 표시예요(undefined도 정상 응답일 수 있어서 따로 둬요). */
export const NOT_HANDLED = Symbol('not-handled');

const AROUND_DAYS = 14;
const MAX_ANCHOR_DAYS = 365;
const CHAT_LIST_LIMIT = 50;
const MAX_MESSAGE_LENGTH = 2000;
const PREVIEW_MAX_CHARS = 40;
const RECOMMENDED_MINUTES = 30;
/** 메시지 사이 간격은 이만큼까지만 대화 시간으로 쳐요(서버와 같은 5분 상한) */
const GAP_CAP_MS = 5 * 60_000;
const SHORTCUT_KEYS: ShortcutKey[] = ['ONE_MONTH', 'SIX_MONTHS', 'ONE_YEAR'];
/** 시점 분석에 걸리는 시간(가짜). 실제 서버는 전후 2주가 약 14초, 하루가 약 9초예요. */
const PREPARE_MS: Record<PersonaScope, number> = { AROUND: 7000, DAY: 4500 };
/** 리포트 만드는 시간(가짜) */
const REPORT_MS = 3000;
/** 답변이 오기 시작하는 데까지 · 조각 사이 간격(가짜) */
const FIRST_TOKEN_MS = 900;
const TOKEN_MS = 60;

const GREETING = '어, 왔어? 오랜만이다 ㅋㅋ';
const REPLIES = [
  '아 진짜? 그때는 그런 생각까지는 못 했는데, 듣고 보니 맞는 것 같아',
  '음… 솔직히 그때는 좀 막막했어. 근데 티는 안 내려고 했지',
  '그렇구나. 네가 그렇게 말해 주니까 좀 위로가 된다 ㅋㅋ',
  '나는 그냥 하루하루 버티는 느낌이었어. 지금의 넌 어때?',
  '와, 그럼 내가 잘 해낸 거네? 다행이다',
  '그건 좀 더 생각해 봐야 할 것 같아. 너는 어떻게 했어?',
];
const DISTRESS_REPLY = '…많이 힘들었구나. 혼자 안고 있지 않아도 돼. 네가 괜찮은지 먼저 듣고 싶어';

/** 서버의 RuleSafetyDetector와 같은 표현이에요(자살 · 자해 · "죽고 싶다" 등). */
const DISTRESS =
  /자살(?!골)|자해|극단적\s?선택|스스로\s?(?:목숨|생을)|목숨을?\s?끊|죽고\s?싶|살기\s?싫|살고\s?싶지\s?않|사라지고\s?싶|없어지고\s?싶|뛰어내리고\s?싶|손목을?\s?긋/;

const SAFETY_NOTICE: SafetyNotice = {
  reason: 'DISTRESS',
  hotlines: [
    { name: '자살예방 상담전화', phone: '109', hours: '24시간' },
    { name: '정신건강 위기상담전화', phone: '1577-0199', hours: '24시간' },
  ],
};

// ───── 시점 고르기 ─────

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

function personaWindow(diaries: Diary[], anchorDate: string, scope: PersonaScope): PersonaWindow {
  const today = todayString();
  const windowStart = scope === 'DAY' ? anchorDate : addDays(anchorDate, -AROUND_DAYS);
  const after = addDays(anchorDate, AROUND_DAYS);
  const windowEnd = scope === 'DAY' ? anchorDate : after > today ? today : after;
  const inRange = diaries.filter((diary) => diary.date >= windowStart && diary.date <= windowEnd);
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
function recordsFingerprint(diaries: Diary[], range: PersonaWindow): string {
  return diaries
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

// ───── 대화 세션 ─────

type ReportState = { kind: 'NONE' } | { kind: 'GENERATING'; readyAt: number } | { kind: 'READY'; report: Report };

type MockChat = {
  id: string;
  shortcutKey: ShortcutKey | null;
  /** 세션을 만들 때의 시점 · 기록량 */
  range: PersonaWindow;
  startedAt: string;
  messages: ChatMessage[];
  /** 분석 중인 동안의 시작 · 끝 시각(ms). 없으면 준비가 끝난 세션이에요. */
  preparing: { from: number; until: number } | null;
  endedAt: string | null;
  /** 답변을 만드는 중이면 true(같은 대화에 또 보내면 409 CHAT_BUSY) */
  busy: boolean;
  /** 끝내기를 부른 뒤의 리포트 상태. 아직 안 끝냈으면 null */
  report: ReportState | null;
};

let chats: MockChat[] | null = null;
let chatSeq = 0;
let messageSeq = 0;
/** 분석을 저장해 둔 시점(`범위|기준일` → 그때의 기록 표). 기록이 안 바뀌었으면 다시 분석하지 않고 바로 만나요. */
const analyses = new Map<string, string>();

const analysisKey = (scope: PersonaScope, anchorDate: string) => `${scope}|${anchorDate}`;

/** 회원 탈퇴로 가짜 서버를 처음 상태로 되돌릴 때 불러요. */
export function resetChatMock() {
  chats = null;
  analyses.clear();
}

function addMessage(chat: MockChat, role: ChatMessage['role'], content: string, at: Date = new Date()): ChatMessage {
  const message: ChatMessage = { id: ++messageSeq, seq: chat.messages.length + 1, role, content, createdAt: at.toISOString() };
  chat.messages.push(message);
  return message;
}

type SeedChat = {
  scope: PersonaScope;
  shortcutKey: ShortcutKey | null;
  anchorDate: string;
  /** 며칠 전 몇 시에 시작했는지 */
  daysAgo: number;
  time: string;
  /** [누가, 무슨 말, 시작한 지 몇 분 뒤] */
  lines: ['USER' | 'PERSONA', string, number][];
  ended?: boolean;
};

/** 목록이 비어 보이지 않게, 스토리보드처럼 지난 대화 세 개를 깔아 둬요(가짜 데이터). 1년 전 대화는 끝나서 리포트가 있어요. */
function seedChats(diaries: Diary[]): MockChat[] {
  const today = todayString();
  const make = ({ scope, shortcutKey, anchorDate, daysAgo, time, lines, ended }: SeedChat): MockChat => {
    const range = personaWindow(diaries, anchorDate, scope);
    analyses.set(analysisKey(scope, anchorDate), recordsFingerprint(diaries, range));
    const start = new Date(`${addDays(today, -daysAgo)}T${time}:00+09:00`);
    const chat: MockChat = {
      id: `mock-chat-${++chatSeq}`,
      shortcutKey,
      range,
      startedAt: start.toISOString(),
      messages: [],
      preparing: null,
      endedAt: null,
      busy: false,
      report: null,
    };
    for (const [role, text, minutes] of lines) {
      addMessage(chat, role, text, new Date(start.getTime() + minutes * 60_000));
    }
    if (ended) {
      const endedAt = new Date(start.getTime() + (lines[lines.length - 1][2] + 1) * 60_000);
      chat.endedAt = endedAt.toISOString();
      chat.report = { kind: 'READY', report: buildReport(chat, endedAt.toISOString()) };
    }
    return chat;
  };
  return [
    make({
      scope: 'AROUND',
      shortcutKey: 'ONE_MONTH',
      anchorDate: shortcutAnchor('ONE_MONTH', today),
      daysAgo: 1,
      time: '20:50',
      lines: [
        ['PERSONA', '어, 왔어? 요즘 학기 끝나가서 정신이 하나도 없어 ㅋㅋ', 0],
        ['USER', '응, 한 달 뒤의 너야. 그때 제일 신경 쓰이던 게 뭐였어?', 1],
        ['PERSONA', '음… 솔직히 진로. 뭘 해야 할지 모르겠는데 티는 못 내겠더라', 2],
        ['USER', '지금은 어느 정도 방향 잡았어. 너무 걱정 마', 4],
        ['PERSONA', '진짜? 그 말 들으니까 좀 마음이 놓인다. 고마워', 5],
      ],
    }),
    make({
      scope: 'DAY',
      shortcutKey: null,
      anchorDate: addDays(today, -4),
      daysAgo: 3,
      time: '20:30',
      lines: [
        ['PERSONA', '아, 그날… 나 진짜 많이 속상했었어', 0],
        ['USER', '무슨 일 있었어? 말해 줘도 돼', 1],
        ['PERSONA', '그날 진짜 많이 속상했어, 근데 말을 못 꺼내겠더라', 2],
      ],
    }),
    make({
      scope: 'AROUND',
      shortcutKey: 'ONE_YEAR',
      anchorDate: shortcutAnchor('ONE_YEAR', today),
      daysAgo: 7,
      time: '22:00',
      ended: true,
      lines: [
        ['PERSONA', '오랜만이다. 요즘은 뭐 하고 지내? 나는 거의 똑같아', 0],
        ['USER', '나는 요즘 팀 프로젝트 하느라 바빠. 그때보다 많이 달라졌어', 1],
        ['PERSONA', '오 진짜? 어떻게 달라졌는데?', 2],
        ['USER', '하고 싶은 걸 조금 알 것 같아', 4],
        ['PERSONA', '와… 그럼 지금은 좀 괜찮은 거네. 다행이다', 5],
      ],
    }),
  ];
}

function chatStore(diaries: Diary[]): MockChat[] {
  chats = chats ?? seedChats(diaries);
  return chats;
}

function findChat(diaries: Diary[], id: string | undefined): MockChat {
  const chat = chatStore(diaries).find((candidate) => candidate.id === id);
  if (!chat) throw notFound();
  return chat;
}

/** 분석이 끝났으면 준비 중 표시를 걷고 첫 인사를 넣어요. 아직 분석 중이면 true */
function isPreparing(chat: MockChat): boolean {
  if (chat.preparing && Date.now() >= chat.preparing.until) {
    chat.preparing = null;
    addMessage(chat, 'PERSONA', GREETING);
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
    endedAt: chat.endedAt,
  };
}

/** 마지막 메시지의 첫 줄을 PREVIEW_MAX_CHARS자까지만 보여줘요(넘으면 …). */
function previewOf(message: ChatMessage | undefined): string | null {
  if (!message) return null;
  const firstLine = message.content.trim().split('\n')[0];
  return firstLine.length <= PREVIEW_MAX_CHARS ? firstLine : `${firstLine.slice(0, PREVIEW_MAX_CHARS)}…`;
}

function chatListItem(chat: MockChat): ChatListItem {
  const session = chatSession(chat);
  const last = chat.messages[chat.messages.length - 1];
  return {
    id: chat.id,
    scope: session.scope,
    shortcutKey: chat.shortcutKey,
    anchorDate: session.anchorDate,
    emotions: chat.range.emotions ?? [],
    level: session.level,
    status: session.status,
    lastMessagePreview: previewOf(last),
    lastMessageAt: last?.createdAt ?? chat.startedAt,
    startedAt: chat.startedAt,
    endedAt: chat.endedAt,
  };
}

// ───── 대화 시간 · 리포트 ─────

/** 연속한 메시지 사이 간격을 더해요(한 간격은 5분까지만). onlyDay를 주면 그날 온 메시지의 간격만 더해요. */
function talkedMs(messages: ChatMessage[], onlyDay?: string): number {
  let total = 0;
  for (let i = 1; i < messages.length; i += 1) {
    const at = new Date(messages[i].createdAt);
    if (onlyDay && dateStringOf(at) !== onlyDay) continue;
    total += Math.min(at.getTime() - new Date(messages[i - 1].createdAt).getTime(), GAP_CAP_MS);
  }
  return total;
}

const toMinutes = (ms: number) => Math.round(ms / 60_000);

function buildReport(chat: MockChat, createdAt: string): Report {
  return {
    id: `mock-report-${chat.id}`,
    chatId: chat.id,
    mode: 'PERSONAL',
    scope: chat.range.scope,
    anchorDate: chat.range.anchorDate,
    durationMinutes: toMinutes(talkedMs(chat.messages)),
    pastConcern: '그 무렵에는 앞으로 뭘 해야 할지 막막했지만, 겉으로는 티를 내지 않았어요.',
    presentChange: '지금은 방향을 조금 잡았고, 걱정을 혼자 안고 있지 않게 됐어요.',
    oneLineSummary: '막막함은 줄고, 해 본 일은 늘었어요.',
    memorySummary: null,
    createdAt,
  };
}

/** 만드는 시간이 지났으면 리포트를 완성해요. */
function settleReport(chat: MockChat) {
  if (chat.report?.kind === 'GENERATING' && Date.now() >= chat.report.readyAt) {
    chat.report = { kind: 'READY', report: buildReport(chat, new Date().toISOString()) };
  }
}

// ───── 답변 스트림 ─────

/**
 * POST /chats/{id}/messages (SSE). token → (safety) → done 순서로 이벤트를 내보내요.
 * 실제 서버처럼 연결이 끊겨도(signal 중단) 답변을 끝까지 만들어 저장하고, 앞 답변이 끝나기 전에 또 보내면 409 CHAT_BUSY예요.
 */
export async function streamChatMessage(
  path: string,
  body: unknown,
  ctx: MockContext,
  onEvent: (event: SseEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  const match = /^\/chats\/([^/]+)\/messages$/.exec(path);
  if (!match) throw notFound();
  const chat = findChat(ctx.diaries, match[1]);

  const input = (body ?? {}) as { content?: unknown };
  const content = typeof input.content === 'string' ? input.content : '';
  if (content.trim().length === 0) throw validationFailed([{ field: 'content', reason: '비어 있을 수 없어요' }]);
  if (content.length > MAX_MESSAGE_LENGTH) {
    throw validationFailed([{ field: 'content', reason: `한 번에 ${MAX_MESSAGE_LENGTH}자까지 보낼 수 있어요` }]);
  }
  if (!ctx.user.aiConsentActive) throw new ApiError(403, 'AI_CONSENT_REQUIRED', 'AI 분석에 동의해야 쓸 수 있어요.');
  if (isPreparing(chat)) throw new ApiError(409, 'CHAT_NOT_READY', '아직 그때의 나를 준비하는 중이에요.');
  if (chat.endedAt) throw new ApiError(409, 'CHAT_ENDED', '이미 끝난 대화예요.');
  if (chat.busy) throw new ApiError(409, 'CHAT_BUSY', '앞의 답변이 아직 오는 중이에요.');

  chat.busy = true;
  try {
    const emit = (event: string, data: unknown) => {
      if (!signal?.aborted) onEvent({ event, data: JSON.stringify(data) });
    };
    if (DISTRESS.test(content)) emit('safety', SAFETY_NOTICE);
    await wait(FIRST_TOKEN_MS);

    const turns = chat.messages.filter((message) => message.role === 'USER').length;
    const reply = DISTRESS.test(content) ? DISTRESS_REPLY : REPLIES[turns % REPLIES.length];
    for (const piece of reply.match(/[\s\S]{1,3}/g) ?? []) {
      emit('token', { text: piece });
      await wait(TOKEN_MS);
    }

    // 답변이 끝나야 내 말과 답변을 한 번에 저장해요
    const userMessage = addMessage(chat, 'USER', content);
    const personaMessage = addMessage(chat, 'PERSONA', reply);
    emit('done', { userMessage, personaMessage });
  } finally {
    chat.busy = false;
  }
}

// ───── 요청 처리 ─────

/** 시점 고르기 · 대화 · 오늘 대화 시간 요청을 처리해요. 이 파일이 맡은 경로가 아니면 NOT_HANDLED를 돌려줘요. */
export function handleChatRoute(
  method: string,
  path: string,
  query: Record<string, string>,
  body: unknown,
  ctx: MockContext,
): unknown {
  const { user, diaries } = ctx;

  // GET /personas/shortcuts — 바로가기 3개 (전후 2주)
  if (method === 'GET' && path === '/personas/shortcuts') {
    const today = todayString();
    const items = SHORTCUT_KEYS.map((key) => ({ ...personaWindow(diaries, shortcutAnchor(key, today), 'AROUND'), key }));
    return { items };
  }

  // GET /personas/preview?anchor&scope — 고른 날짜의 기록량
  if (method === 'GET' && path === '/personas/preview') {
    const anchor = checkAnchor(query.anchor, 'anchor');
    const scope = query.scope ?? 'DAY';
    if (scope !== 'DAY' && scope !== 'AROUND') {
      throw validationFailed([{ field: 'scope', reason: 'DAY 또는 AROUND여야 해요' }]);
    }
    return personaWindow(diaries, anchor, scope);
  }

  // GET /me/usage/today — 오늘 대화한 시간(메시지 사이 간격의 합, 한 간격 5분 상한)
  if (method === 'GET' && path === '/me/usage/today') {
    const today = todayString();
    const ms = chatStore(diaries).reduce((sum, chat) => sum + talkedMs(chat.messages, today), 0);
    return { date: today, chatMinutes: toMinutes(ms), recommendedMinutes: RECOMMENDED_MINUTES };
  }

  // GET /chats — 과거의 나 목록 (최근 대화 순)
  if (method === 'GET' && path === '/chats') {
    const items = chatStore(diaries)
      .map(chatListItem)
      .sort((a, b) => b.lastMessageAt.localeCompare(a.lastMessageAt))
      .slice(0, CHAT_LIST_LIMIT);
    return { items };
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
    if (!user.aiConsentActive) throw new ApiError(403, 'AI_CONSENT_REQUIRED', 'AI 분석에 동의해야 쓸 수 있어요.');
    if (input.forCouple) throw new ApiError(403, 'COUPLE_ACCESS_DENIED', '커플 모드는 아직 쓸 수 없어요.');

    const range = personaWindow(diaries, checkAnchor(input.anchorDate, 'anchorDate'), scope);
    const key = analysisKey(scope, range.anchorDate);
    const fingerprint = recordsFingerprint(diaries, range);
    // 기록이 하나도 없으면(NONE) 분석 없이 바로 기본 말투로 대화해요
    const needsAnalysis = range.level !== 'NONE' && analyses.get(key) !== fingerprint;
    if (needsAnalysis) analyses.set(key, fingerprint);

    const now = Date.now();
    const chat: MockChat = {
      id: `mock-chat-${++chatSeq}`,
      shortcutKey: input.shortcutKey ?? null,
      range,
      startedAt: new Date(now).toISOString(),
      messages: [],
      preparing: needsAnalysis ? { from: now, until: now + PREPARE_MS[scope] } : null,
      endedAt: null,
      busy: false,
      report: null,
    };
    // 준비가 필요 없으면 바로 첫 인사가 들어 있고, 분석이 끝나면 isPreparing에서 넣어요
    if (!needsAnalysis) addMessage(chat, 'PERSONA', GREETING);
    chatStore(diaries).push(chat);
    return chatSession(chat);
  }

  // GET /chats/{id} — 세션 상태 (PREPARING이면 앱이 1~2초 간격으로 조회해요)
  const chatMatch = /^\/chats\/([^/]+)$/.exec(path);
  if (method === 'GET' && chatMatch) {
    return chatSession(findChat(diaries, chatMatch[1]));
  }

  // GET /chats/{id}/messages?afterSeq&limit — 대화 내용
  const messagesMatch = /^\/chats\/([^/]+)\/messages$/.exec(path);
  if (method === 'GET' && messagesMatch) {
    const chat = findChat(diaries, messagesMatch[1]);
    const afterSeq = Number(query.afterSeq ?? 0);
    const limit = Number(query.limit ?? 50);
    if (!Number.isInteger(afterSeq) || afterSeq < 0) {
      throw validationFailed([{ field: 'afterSeq', reason: '0 이상이어야 해요' }]);
    }
    if (!Number.isInteger(limit) || limit < 1 || limit > 200) {
      throw validationFailed([{ field: 'limit', reason: '1 이상 200 이하여야 해요' }]);
    }
    isPreparing(chat);
    return { items: chat.messages.filter((message) => message.seq > afterSeq).slice(0, limit) };
  }

  // POST /chats/{id}/end — 대화 끝내기. 이미 끝난 대화에 다시 불러도 같은 결과예요.
  const endMatch = /^\/chats\/([^/]+)\/end$/.exec(path);
  if (method === 'POST' && endMatch) {
    const chat = findChat(diaries, endMatch[1]);
    if (!user.aiConsentActive) throw new ApiError(403, 'AI_CONSENT_REQUIRED', 'AI 분석에 동의해야 쓸 수 있어요.');
    if (isPreparing(chat)) throw new ApiError(409, 'CHAT_NOT_READY', '아직 그때의 나를 준비하는 중이에요.');
    if (chat.busy) throw new ApiError(409, 'CHAT_BUSY', '답변이 아직 오는 중이에요. 잠시 뒤에 끝내 주세요.');

    chat.endedAt = chat.endedAt ?? new Date().toISOString();
    const myMessages = chat.messages.filter((message) => message.role === 'USER').length;
    // 내 말이 2개 미만이면 끝낼 수는 있지만 리포트는 만들지 않아요
    if (myMessages < 2) chat.report = { kind: 'NONE' };
    else chat.report = chat.report ?? { kind: 'GENERATING', readyAt: Date.now() + REPORT_MS };
    settleReport(chat);
    const result: components['schemas']['EndChatResult'] = {
      chatId: chat.id,
      endedAt: chat.endedAt,
      durationMinutes: toMinutes(talkedMs(chat.messages)),
      reportStatus: chat.report.kind === 'READY' ? 'READY' : chat.report.kind === 'GENERATING' ? 'GENERATING' : 'NONE',
    };
    return result;
  }

  // GET /chats/{id}/report — 만드는 중이면 202(본문은 { status: 'GENERATING' })
  const reportMatch = /^\/chats\/([^/]+)\/report$/.exec(path);
  if (method === 'GET' && reportMatch) {
    const chat = findChat(diaries, reportMatch[1]);
    if (!chat.endedAt) throw new ApiError(404, 'CHAT_NOT_ENDED', '아직 끝나지 않은 대화예요.');
    settleReport(chat);
    if (!chat.report || chat.report.kind === 'NONE') {
      throw new ApiError(404, 'REPORT_NOT_AVAILABLE', '이야기가 짧아서 리포트를 만들지 않았어요.');
    }
    return chat.report.kind === 'GENERATING' ? { status: 'GENERATING' } : chat.report.report;
  }

  return NOT_HANDLED;
}
