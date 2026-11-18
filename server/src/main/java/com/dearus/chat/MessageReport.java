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

/**
 * message_reports 테이블 (V11). 사용자가 AI의 답변을 신고한 기록이에요.
 * Dear Us 관리자가 확인하면 reviewed_at을 채우고(지금은 Supabase SQL 편집기에서), 프롬프트를 고쳐요.
 * 메시지가 지워지면 DB에서 함께 지워져요.
 */
@Entity
@Table(name = "message_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageReport {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "message_id", nullable = false, updatable = false)
	private Long messageId;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(nullable = false, updatable = false, length = 20)
	@Enumerated(EnumType.STRING)
	private MessageReportReason reason;

	@Column(updatable = false, columnDefinition = "text")
	private String detail;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "reviewed_at")
	private OffsetDateTime reviewedAt;

	public static MessageReport create(Long messageId, UUID userId, MessageReportReason reason, String detail) {
		MessageReport report = new MessageReport();
		report.messageId = messageId;
		report.userId = userId;
		report.reason = reason;
		report.detail = detail;
		report.createdAt = Kst.now();
		return report;
	}
}
