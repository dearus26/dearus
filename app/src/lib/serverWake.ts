import { useSyncExternalStore } from 'react';

/**
 * 서버가 잠에서 깨는 중인지 알려줘요(2026.11.28). 무료 서버(Render)는 15분 동안 요청이 없으면 잠들어서,
 * 그다음 첫 요청은 5~10초쯤 걸려 앱이 멈춘 것처럼 보여요. 요청이 SLOW_MS보다 오래 걸리면 "깨우는 중"으로 보고,
 * 그런 요청이 모두 끝나면 다시 꺼요. 화면은 맨 위 안내 띠(ServerWakeBanner)로 보여줘요.
 * 채팅 답변(SSE)은 원래 오래 이어지는 요청이라 세지 않아요.
 */
export const SLOW_MS = 4_000;

let slowCount = 0;
const listeners = new Set<() => void>();

function emit() {
  listeners.forEach((listener) => listener());
}

/** 요청을 감싸서, SLOW_MS가 지나도 끝나지 않으면 "깨우는 중"으로 세요. */
export function trackSlow<T>(promise: Promise<T>): Promise<T> {
  let counted = false;
  const timer = setTimeout(() => {
    counted = true;
    slowCount += 1;
    emit();
  }, SLOW_MS);
  return promise.finally(() => {
    clearTimeout(timer);
    if (counted) {
      slowCount -= 1;
      emit();
    }
  });
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/** 오래 걸리는 요청이 하나라도 있으면 true예요. */
export function useServerWaking(): boolean {
  return useSyncExternalStore(subscribe, () => slowCount > 0);
}
