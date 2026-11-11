import type { MessengerMessage, PickedImage } from '@/api/uploads';
import type { ParsedChat } from '@/features/messenger/kakaoTxt';

/**
 * 메신저 올리기 화면들이 주고받는 값을 폰 메모리에 잠깐 들고 있는 곳이에요(저장하지 않아요. 앱을 끄면 사라져요).
 *  - parsedChat: txt를 읽은 결과. "내 이름 고르기" 화면이 이어서 써요.
 *  - pendingUpload: 올린 내용. 서버가 DONE을 줄 때까지 들고 있다가, 실패하면 같은 내용을 다시 올려요
 *    (명세: txt는 앱이 추린 글자를 폰에 갖고 있다가 다시 보내고, 이미지는 사진을 다시 올려요).
 */
export type PendingUpload =
  | { kind: 'TXT'; messages: MessengerMessage[] }
  | { kind: 'IMAGE'; images: PickedImage[] };

let parsedChat: ParsedChat | null = null;
let pendingUpload: PendingUpload | null = null;

export const setParsedChat = (value: ParsedChat | null) => {
  parsedChat = value;
};
export const getParsedChat = () => parsedChat;

export const setPendingUpload = (value: PendingUpload | null) => {
  pendingUpload = value;
};
export const getPendingUpload = () => pendingUpload;
