import { ApiError, UNAUTHORIZED_MESSAGE } from '@/api/errors';
import { NOT_HANDLED, handleChatRoute, resetChatMock, streamChatMessage } from '@/api/mock/chat';
import { notFound, sortedEmotions, validationFailed, wait } from '@/api/mock/helpers';
import type { components } from '@/api/schema';
import type { SseEvent } from '@/api/sse';
import { addDays, daysBetween, isValidDateString, todayString } from '@/lib/date';

type User = components['schemas']['User'];
type AuthResponse = components['schemas']['AuthResponse'];
type Diary = components['schemas']['Diary'];
type Emotion = components['schemas']['Emotion'];

/**
 * 서버 없이 앱을 만들 때 쓰는 가짜 서버예요(EXPO_PUBLIC_API_MODE=mock).
 * 요청 · 응답 모양은 server/openapi.yaml을 따라요. 화면은 이걸 몰라요 — api/ 함수만 불러요.
 * 여기에는 로그인 · 동의 · 일기가 있고, 시점 고르기 · 대화 · 리포트는 chat.ts에 있어요.
 * 메모리에만 있어서 앱을 완전히 다시 켜면 처음 상태로 돌아가요.
 */

const MOCK_TOKEN = 'mock-access-token';
const DELAY_MS = 350;
const RETENTION_DAYS = 30;
const MAX_RANGE_DAYS = 93;
const MAX_CONTENT = 5000;

let user: User | null = null;
let diaries: Diary[] | null = null;
let diarySeq = 0;

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

/** 로그인한 사용자를 확인하고 지금 사용자를 돌려줘요. 토큰이 없거나 틀리면 401이에요. */
function signedInUser(token: string | null): User {
  if (token !== MOCK_TOKEN) {
    throw new ApiError(401, 'UNAUTHORIZED', UNAUTHORIZED_MESSAGE);
  }
  // 앱을 껐다 켜서 가짜 서버가 초기화됐어도 저장된 토큰으로 이어서 쓰게 해요
  user = user ?? newUser('dev');
  return user;
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
  signedInUser(token);
  const current = user as User;

  // GET /me
  if (method === 'GET' && path === '/me') {
    return current as T;
  }

  // PUT /me/consent — 서비스 이용 동의
  if (method === 'PUT' && path === '/me/consent') {
    const input = (body ?? {}) as Partial<components['schemas']['UserConsentRequest']>;
    const missing = (['terms', 'privacy', 'aiAnalysis'] as const)
      .filter((key) => input[key] !== true)
      .map((field) => ({ field, reason: '꼭 동의해야 해요' }));
    if (missing.length > 0) throw validationFailed(missing);

    // 이미 유효한 동의에 다시 불러도 시각은 그대로 두고 알림 설정만 바꿔요
    let next = current;
    if (!next.aiConsentActive) {
      next = { ...next, aiConsentedAt: new Date().toISOString(), aiRevokedAt: null, aiDataDeleteAt: null };
    }
    user = {
      ...next,
      aiConsentActive: true,
      needsConsent: false,
      notificationsOptIn: input.notifications ?? next.notificationsOptIn,
    };
    return user as T;
  }

  // DELETE /me/consent — AI 분석 동의 철회 (분석 데이터는 30일 뒤 삭제)
  if (method === 'DELETE' && path === '/me/consent') {
    if (current.aiConsentActive) {
      const now = new Date();
      user = {
        ...current,
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
    resetChatMock();
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

  // 시점 고르기 · 대화 · 리포트 · 오늘 대화 시간
  const chatResult = handleChatRoute(method, path, query, body, { user: current, diaries: store() });
  if (chatResult !== NOT_HANDLED) return chatResult as T;

  throw notFound();
}

/** SSE로 답변을 받는 요청(POST /chats/{id}/messages)의 가짜 서버예요. 이벤트를 onEvent로 하나씩 내보내요. */
export async function mockStreamRequest(
  fullPath: string,
  body: unknown,
  token: string | null,
  onEvent: (event: SseEvent) => void,
  signal?: AbortSignal,
): Promise<void> {
  await wait(DELAY_MS);
  signedInUser(token);
  const { path } = splitPath(fullPath);
  await streamChatMessage(path, body, { user: user as User, diaries: store() }, onEvent, signal);
}
