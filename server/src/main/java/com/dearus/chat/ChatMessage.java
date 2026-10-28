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
		ChatMessage message = new ChatMessage();
		message.sessionId = sessionId;
		message.seq = seq;
		message.role = role;
		message.content = content;
		message.createdAt = Kst.now();
		return message;
	}
}
