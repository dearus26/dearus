import type { MessengerMessage } from '@/api/uploads';
import type { ParsedChat } from '@/features/messenger/kakaoTxt';

/**
 * 메신저 올리기 화면들이 주고받는 값을 폰 메모리에 잠깐 들고 있는 곳이에요(저장하지 않아요. 앱을 끄면 사라져요).
 *  - targetDate: 첫 화면에서 넘어온 날('YYYY-MM-DD'). 내 말 확인 화면에서 이 날을 먼저 골라 두고, 다른 날도 함께 고를 수 있어요(2026.11.28, 최대 14일).
 *  - parsedChat: txt를 읽은 결과. "내 이름 고르기" 화면이 이어서 써요.
 *  - pendingUpload: 올린 내용(앱이 추린 내 말). 서버가 DONE을 줄 때까지 들고 있다가, 실패하면 같은 내용을 다시 올려요.
 */
export type PendingUpload = { messages: MessengerMessage[] };

let targetDate: string | null = null;
let parsedChat: ParsedChat | null = null;
let pendingUpload: PendingUpload | null = null;

export const setTargetDate = (value: string | null) => {
  targetDate = value;
};
export const getTargetDate = () => targetDate;

export const setParsedChat = (value: ParsedChat | null) => {
  parsedChat = value;
};
export const getParsedChat = () => parsedChat;

export const setPendingUpload = (value: PendingUpload | null) => {
  pendingUpload = value;
};
export const getPendingUpload = () => pendingUpload;
