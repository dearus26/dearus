package com.dearus.common.ai;

import java.util.List;

/**
 * AI에 보낼 요청. maxTokens에는 생각(thinking)에 쓰는 토큰도 포함돼요.
 * 마지막 메시지는 USER여야 해요 (Claude Haiku 5.5는 assistant 메시지로 끝나는 요청을 거절해요).
 */
public record AiRequest(String system, List<AiMessage> messages, int maxTokens, AiEffort effort) {

	public AiRequest {
		if (messages == null || messages.isEmpty()) {
			throw new IllegalArgumentException("메시지가 하나 이상 있어야 해요");
		}
		if (messages.get(messages.size() - 1).role() != AiMessage.Role.USER) {
			throw new IllegalArgumentException("마지막 메시지는 USER여야 해요");
		}
		if (maxTokens <= 0) {
			throw new IllegalArgumentException("maxTokens는 0보다 커야 해요");
		}
	}

	/** 사용자 메시지 하나로 하는 요청 (요약 같은 한 번짜리 호출). */
	public static AiRequest single(String system, String userText, int maxTokens, AiEffort effort) {
		return new AiRequest(system, List.of(new AiMessage(AiMessage.Role.USER, userText)), maxTokens, effort);
	}
}
