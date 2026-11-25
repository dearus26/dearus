package com.dearus.common.ai;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 서버가 켜지면 AI 연결을 미리 열어 둬요(2026.11.25). Render 무료 요금제는 자주 잠들었다 깨서, 깰 때마다 첫 대화가 느려지지 않게 해요.
 * 서버가 요청을 받기 시작한 뒤 따로 돌아서 켜지는 시간을 늘리지 않아요. 가짜 AI(app.ai.provider=fake)는 아무것도 하지 않아요.
 */
@Component
public class AiWarmUp {

	private final AiClient ai;

	public AiWarmUp(AiClient ai) {
		this.ai = ai;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void warmUp() {
		Thread.ofVirtual().name("ai-warm-up").start(ai::warmUp);
	}
}
