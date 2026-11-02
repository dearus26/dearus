package com.dearus.chat;

import com.dearus.common.Kst;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** chat_messages 테이블 (ERD). seq는 세션 안에서 1부터 늘어나고, 1번은 페르소나의 첫 인사예요. */
@Entity
@Table(name = "chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "session_id", nullable = false, updatable = false)
	private UUID sessionId;

	@Column(nullable = false, updatable = false)
	private int seq;

	@Column(nullable = false, updatable = false, length = 10)
	@Enumerated(EnumType.STRING)
	private ChatRole role;

	@Column(nullable = false, updatable = false, columnDefinition = "text")
	private String content;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	public static ChatMessage create(UUID sessionId, int seq, ChatRole role, String content) {
		return create(sessionId, seq, role, content, Kst.now());
	}

	/** 시각을 직접 정해요. 내 말은 답변이 끝나서 저장돼도 보낸 시각으로 남기려고 써요. */
	public static ChatMessage create(UUID sessionId, int seq, ChatRole role, String content, OffsetDateTime createdAt) {
		ChatMessage message = new ChatMessage();
		message.sessionId = sessionId;
		message.seq = seq;
		message.role = role;
		message.content = content;
		message.createdAt = createdAt;
		return message;
	}
}
