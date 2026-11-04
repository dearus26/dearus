import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';

import { listMessages, sendMessage, type ChatMessage, type SafetyNotice } from '@/api/chats';
import { ApiError, errorMessage } from '@/api/errors';
import { chatKeys, useMessages, usageKeys } from '@/features/past/hooks';

/** 연결이 끊겼을 때 서버가 저장한 답변을 이어받아 보는 횟수 · 간격(서버는 끊겨도 답변을 끝까지 만들어 저장해요) */
const RECOVER_TRIES = 8;
const RECOVER_INTERVAL_MS = 2000;

/** 방금 보낸 내 말과, 도착하는 중인 답변이에요. 답변이 저장돼 `done`이 오면 서버가 준 메시지로 바뀌어요. */
export type Draft = {
  user: string;
  reply: string;
  /** 연결이 끊겨서 저장된 답변이 있는지 확인하는 중 */
  recovering: boolean;
};

/** 보내기에 실패하면 message를 알려주고, 같은 글을 다시 보낼 수 있게 화면이 입력창을 되돌려요. */
export type SendResult = { ok: true } | { ok: false; message: string };

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

/** seq가 같은 메시지는 하나로 합쳐요(서버에서 다시 받은 것과 이미 받은 것이 겹칠 수 있어요). */
function mergeMessages(current: ChatMessage[], added: ChatMessage[]): ChatMessage[] {
  const known = new Set(current.map((message) => message.seq));
  return [...current, ...added.filter((message) => !known.has(message.seq))].sort((a, b) => a.seq - b.seq);
}

/**
 * 대화 한 방의 내용과 말 걸기를 맡아요.
 *  - 지난 메시지는 서버에서 받아요(useMessages).
 *  - 말을 보내면 내 말을 바로 보여주고 답변을 SSE로 받는 대로 이어 붙여요(draft).
 *  - 답변이 끝나야 서버가 저장해서 `done`이 오고, 그때 draft가 저장된 메시지로 바뀌어요.
 *  - 위기 신호가 감지되면 답변보다 먼저 safety가 채워져요(안전 안내 시트).
 */
export function useConversation(chatId: string) {
  const queryClient = useQueryClient();
  const query = useMessages(chatId);
  // 이번 화면에서 새로 받은 메시지. 서버 목록과 합쳐서 보여주고, 서버가 다시 내려주면 같은 seq끼리 하나로 합쳐져요.
  const [extra, setExtra] = useState<ChatMessage[]>([]);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [safety, setSafety] = useState<SafetyNotice | null>(null);
  const sending = useRef(false);
  const controller = useRef<AbortController | null>(null);

  // 화면을 벗어나면 받던 스트림을 멈춰요. 서버는 답변을 끝까지 만들어 저장하니, 돌아오면 목록에 와 있어요.
  useEffect(() => () => controller.current?.abort(), []);

  const messages = mergeMessages(query.data ?? [], extra);

  /** 받은 메시지를 보여주고 draft를 치워요. 한 번에 바꿔서 내 말이 깜빡이지 않아요. */
  function commit(received: ChatMessage[]) {
    setExtra((current) => mergeMessages(current, received));
    setDraft(null);
  }

  /** 연결이 끊긴 뒤에 서버가 저장해 둔 답변이 있는지 몇 번 이어받아 봐요. */
  async function recover(afterSeq: number, signal: AbortSignal): Promise<boolean> {
    for (let attempt = 0; attempt < RECOVER_TRIES; attempt += 1) {
      await sleep(RECOVER_INTERVAL_MS);
      if (signal.aborted) return false;
      try {
        const { items } = await listMessages(chatId, afterSeq);
        if (items.length > 0) {
          commit(items);
          return true;
        }
      } catch {
        // 아직 연결이 안 돌아왔어요. 다음 확인 때 다시 해요.
      }
    }
    return false;
  }

  async function send(content: string): Promise<SendResult> {
    if (sending.current) return { ok: false, message: '앞의 답변이 아직 오는 중이에요.' };
    sending.current = true;
    const abort = new AbortController();
    controller.current = abort;
    const afterSeq = messages[messages.length - 1]?.seq ?? 0;
    setDraft({ user: content, reply: '', recovering: false });

    try {
      const reply = await sendMessage(chatId, content, {
        signal: abort.signal,
        onToken: (text) => setDraft((current) => current && { ...current, reply: current.reply + text }),
        onSafety: setSafety,
      });
      commit([reply.userMessage, reply.personaMessage]);
      return { ok: true };
    } catch (error) {
      if (abort.signal.aborted) return { ok: false, message: '' };
      if (error instanceof ApiError && error.status === 0) {
        setDraft((current) => current && { ...current, recovering: true });
        if (await recover(afterSeq, abort.signal)) return { ok: true };
        setDraft(null);
        return { ok: false, message: '답변을 받지 못했어요. 같은 말을 다시 보내 주세요.' };
      }
      setDraft(null);
      // 끝난 대화 · 준비 중 · 앞 답변 진행 중이면 화면이 가진 상태가 낡은 거라서 새로 받아요
      if (error instanceof ApiError && ['CHAT_ENDED', 'CHAT_NOT_READY', 'CHAT_BUSY'].includes(error.code)) {
        void queryClient.invalidateQueries({ queryKey: chatKeys.detail(chatId) });
        void queryClient.invalidateQueries({ queryKey: chatKeys.messages(chatId) });
      }
      return { ok: false, message: errorMessage(error) };
    } finally {
      sending.current = false;
      // 목록의 마지막 말 · 시간과 오늘 대화 시간이 바뀌었어요
      void queryClient.invalidateQueries({ queryKey: chatKeys.list });
      void queryClient.invalidateQueries({ queryKey: usageKeys.today });
    }
  }

  return {
    messages,
    loading: query.isPending,
    failed: query.isError && query.data === undefined,
    error: query.error,
    refetch: () => void query.refetch(),
    draft,
    safety,
    dismissSafety: () => setSafety(null),
    send,
  };
}
