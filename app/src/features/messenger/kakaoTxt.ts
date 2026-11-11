/**
 * 카카오톡 "대화 내보내기"로 만든 txt를 읽어서 (말한 사람 · 날짜 · 내용)으로 나눠요.
 * 폰 안에서만 쓰는 순수 함수라서 다른 파일에 기대지 않아요(그래서 날짜 계산도 여기서 해요).
 *
 * 읽는 형식 (기기 · 버전마다 달라서 여러 가지를 한꺼번에 받아요):
 *  - 모바일(안드로이드 · 아이폰): `2026년 11월 3일 오후 10:15, 철수 : 안녕`, `2026. 11. 3. 오후 10:15, 철수 : 안녕`,
 *    `2026-11-03 22:15, 철수 : 안녕`처럼 메시지마다 날짜가 붙어요.
 *  - PC: `--------------- 2026년 11월 3일 월요일 ---------------` 아래에 `[철수] [오후 10:15] 안녕`처럼 시각만 붙어요.
 *  - 여러 줄로 쓴 메시지는 다음 줄들이 시각 없이 이어져요. 입장 · 퇴장 안내와 사진 · 이모티콘만 있는 메시지는 건너뛰어요.
 */

export type ParsedMessage = {
  speaker: string;
  /** 'YYYY-MM-DD' */
  date: string;
  /** 내 말 그대로예요(가리지 않아요). 여러 줄이면 줄바꿈(\n)을 그대로 둬요. */
  text: string;
};

export type ParsedChat = {
  messages: ParsedMessage[];
  /** 말한 사람과 말한 횟수(많은 순) */
  speakers: { name: string; count: number }[];
  /** 입장 · 퇴장 안내, 사진 · 이모티콘만 있는 메시지처럼 건너뛴 것의 수 */
  skipped: number;
  /** 메시지처럼 보이지만 날짜를 알 수 없어 읽지 못한 것의 수(사용자에게 알려요) */
  unreadable: number;
};

// 모바일 한 줄: 날짜 시각, 이름 : 내용 (오전 · 오후는 없을 수도 있어요)
const MOBILE_LINE =
  /^(\d{4})[년.\-/]\s?(\d{1,2})[월.\-/]\s?(\d{1,2})[일.]?\s+(?:(오전|오후)\s+)?(\d{1,2}):(\d{2})(?::\d{2})?,\s+(.+?)\s:\s(.*)$/;
// 모바일에서 날짜 시각으로 시작하지만 "이름 : 내용"이 아닌 줄 = 시스템 안내(입장 · 퇴장 등)
const MOBILE_PREFIX = /^\d{4}[년.\-/]\s?\d{1,2}[월.\-/]\s?\d{1,2}[일.]?\s+(?:(?:오전|오후)\s+)?\d{1,2}:\d{2}(?::\d{2})?,\s/;
// PC 메시지: [이름] [오후 10:15] 내용
const PC_LINE = /^\[(.+?)\]\s+\[(오전|오후)\s+(\d{1,2}):(\d{2})\]\s?(.*)$/;
// 날짜 구분선: --------------- 2026년 11월 3일 월요일 --------------- 또는 2026년 11월 3일 월요일
const DATE_SEPARATOR = /^(?:-{3,}\s*)?(\d{4})년\s?(\d{1,2})월\s?(\d{1,2})일(?:\s+[월화수목금토일]요일)?(?:\s*-{3,})?\s*$/;
// 시각이 없는 시스템 안내(PC)
const SYSTEM_TEXT =
  /님(?:이|을)\s.*(?:들어왔습니다|나갔습니다|초대했습니다|내보냈습니다)\.?$|님이 (?:들어왔습니다|나갔습니다)\.?$|^채팅방 관리자가 .*$/;
// 글자 없이 사진 · 이모티콘만 보낸 메시지
const MEDIA_ONLY =
  /^(?:사진|동영상|이모티콘|음성메시지|보이스톡|사진 \d+장|\(이모티콘\)|\(사진\)|\(동영상\)|삭제된 메시지입니다\.?|파일: .*)$/;

function pad(n: number): string {
  return String(n).padStart(2, '0');
}

/** 실제로 있는 날짜면 'YYYY-MM-DD', 아니면 null */
function toDate(year: number, month: number, day: number): string | null {
  const date = new Date(year, month - 1, day);
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) return null;
  return `${year}-${pad(month)}-${pad(day)}`;
}

export function parseKakaoTxt(raw: string): ParsedChat {
  const lines = raw.replace(/^\uFEFF/, '').split(/\r?\n/);
  const messages: ParsedMessage[] = [];
  let skipped = 0;
  let unreadable = 0;

  // PC 형식은 날짜가 구분선에 한 번만 나와서 마지막으로 본 날짜를 들고 있어요
  let currentDate: string | null = null;
  // 아직 끝나지 않은(다음 줄이 이어질 수 있는) 메시지
  let open: { speaker: string; date: string; lines: string[] } | null = null;

  function flush() {
    if (!open) return;
    const text = open.lines.join('\n').replace(/\s+$/, '');
    if (MEDIA_ONLY.test(text)) skipped += 1;
    else if (text.length > 0) messages.push({ speaker: open.speaker, date: open.date, text });
    open = null;
  }

  for (const line of lines) {
    const mobile = MOBILE_LINE.exec(line);
    if (mobile) {
      flush();
      const date = toDate(Number(mobile[1]), Number(mobile[2]), Number(mobile[3]));
      if (date === null) unreadable += 1;
      else open = { speaker: mobile[7].trim(), date, lines: [mobile[8]] };
      continue;
    }

    const pc = PC_LINE.exec(line);
    if (pc) {
      flush();
      if (currentDate === null) unreadable += 1;
      else open = { speaker: pc[1].trim(), date: currentDate, lines: [pc[5]] };
      continue;
    }

    const separator = DATE_SEPARATOR.exec(line.trim());
    if (separator) {
      flush();
      currentDate = toDate(Number(separator[1]), Number(separator[2]), Number(separator[3]));
      continue;
    }

    if (MOBILE_PREFIX.test(line) || SYSTEM_TEXT.test(line.trim())) {
      flush();
      skipped += 1;
      continue;
    }

    // 위 어느 것도 아니면: 열려 있는 메시지의 다음 줄이에요(첫 메시지 앞의 머리말은 버려요)
    if (open) open.lines.push(line);
  }
  flush();

  const counts = new Map<string, number>();
  for (const message of messages) counts.set(message.speaker, (counts.get(message.speaker) ?? 0) + 1);
  const speakers = [...counts.entries()]
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count || a.name.localeCompare(b.name));

  return { messages, speakers, skipped, unreadable };
}
