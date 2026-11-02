package com.dearus.chat;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * API 명세의 ChatReply. 답변이 저장된 뒤의 결과예요(SSE의 done 이벤트와 JSON 응답이 같은 모양이에요).
 * safety는 위기 신호를 감지했을 때만 있어요(JSON 응답에서만 쓰고, SSE에서는 safety 이벤트로 먼저 와요).
 */
public record ChatReplyResponse(
	ChatMessageResponse userMessage,
	ChatMessageResponse personaMessage,
	@JsonInclude(JsonInclude.Include.NON_NULL) SafetyNotice safety) {
}
