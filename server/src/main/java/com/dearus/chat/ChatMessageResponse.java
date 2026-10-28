package com.dearus.chat;

import com.dearus.common.Kst;
import java.time.OffsetDateTime;

/** API 명세의 ChatMessage. */
public record ChatMessageResponse(long id, int seq, ChatRole role, String content, OffsetDateTime createdAt) {

	public static ChatMessageResponse from(ChatMessage message) {
		return new ChatMessageResponse(message.getId(), message.getSeq(), message.getRole(), message.getContent(),
			Kst.toKst(message.getCreatedAt()));
	}
}
