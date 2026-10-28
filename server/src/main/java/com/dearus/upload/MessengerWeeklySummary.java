package com.dearus.upload;

import com.dearus.common.Kst;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * messenger_weekly_summaries 테이블 (ERD). 주 단위 요약 · 말투 분석 · 대표 문장을 암호화해서 담아요.
 * 읽을 때는 CryptoService로 풀어요(페르소나를 만들 때).
 */
@Entity
@Table(name = "messenger_weekly_summaries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessengerWeeklySummary {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "upload_id", nullable = false, updatable = false)
	private UUID uploadId;

	/** 그 주의 월요일 */
	@Column(name = "week_start", nullable = false, updatable = false)
	private LocalDate weekStart;

	@Column(name = "summary_enc", nullable = false, updatable = false)
	private byte[] summaryEnc;

	@Column(name = "traits_enc", nullable = false, updatable = false)
	private byte[] traitsEnc;

	/** 말투가 드러나는 대표 문장 목록(JSON 배열)을 암호화한 값 */
	@Column(name = "sample_enc", nullable = false, updatable = false)
	private byte[] sampleEnc;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private OffsetDateTime expiresAt;

	public static MessengerWeeklySummary create(UUID uploadId, LocalDate weekStart, byte[] summaryEnc, byte[] traitsEnc,
		byte[] sampleEnc, OffsetDateTime expiresAt) {
		MessengerWeeklySummary summary = new MessengerWeeklySummary();
		summary.uploadId = uploadId;
		summary.weekStart = weekStart;
		summary.summaryEnc = summaryEnc;
		summary.traitsEnc = traitsEnc;
		summary.sampleEnc = sampleEnc;
		summary.createdAt = Kst.now();
		summary.expiresAt = expiresAt;
		return summary;
	}
}
