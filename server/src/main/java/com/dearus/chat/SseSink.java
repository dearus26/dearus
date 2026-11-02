package com.dearus.chat;

import java.io.IOException;
import java.util.Map;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 답변 글자와 위기 신호를 SSE 이벤트(token · safety · done · error)로 내보내요.
 * 클라이언트가 끊기거나 SSE 시간이 지나 보내기에 실패해도 예외를 던지지 않고 이후 이벤트를 건너뛰어요.
 * 그래야 답변 만들기와 저장이 끝까지 돌아요(앱이 다시 열릴 때 이어받으니까요).
 */
class SseSink implements ChatReplyService.Sink {

	private final SseEmitter emitter;
	private volatile boolean open = true;

	SseSink(SseEmitter emitter) {
		this.emitter = emitter;
	}

	@Override
	public void safety(SafetyNotice notice) {
		send("safety", notice);
	}

	@Override
	public void token(String text) {
		send("token", Map.of("text", text));
	}

	/** done · error 같은 이벤트를 보내요. */
	void send(String event, Object data) {
		if (!open) {
			return;
		}
		try {
			emitter.send(SseEmitter.event().name(event).data(data));
		} catch (IOException | IllegalStateException e) {
			open = false; // 끊겼어요. 더 보내지 않아요.
		}
	}

	/** 스트림을 닫아요(이미 끊겼으면 아무것도 안 해요). */
	void complete() {
		if (!open) {
			return;
		}
		try {
			emitter.complete();
		} catch (RuntimeException ignored) {
			open = false;
		}
	}
}
