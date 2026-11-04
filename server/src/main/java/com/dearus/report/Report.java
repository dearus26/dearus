package com.dearus.report;

import com.dearus.common.Kst;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * reports 테이블 (ERD). 대화 세션 하나에 한 건이에요. 끝내기를 누르면 GENERATING(또는 NONE)으로 만들어지고,
 * AI가 끝나면 READY, 실패하면 FAILED + failureCode가 돼요. memory_summary · gap_summary는 커플 모드에서 쓰는 컬럼이라 아직 매핑하지 않았어요.
 */
@Entity
@Table(name = "reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "session_id", nullable = false, updatable = false)
	private UUID sessionId;

	@Column(nullable = false, length = 20)
	@Enumerated(EnumType.STRING)
	private ReportStatus status;

	@Column(name = "failure_code", length = 40)
	private String failureCode;

	@Column(name = "duration_minutes", nullable = false, updatable = false)
	private int durationMinutes;

	@Column(name = "past_concern", columnDefinition = "text")
	private String pastConcern;

	@Column(name = "present_change", columnDefinition = "text")
	private String presentChange;

	@Column(name = "one_line_summary", length = 100)
	private String oneLineSummary;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	/** 방금 끝낸 대화의 리포트예요. AI가 만들기 시작해요. */
	public static Report generating(UUID sessionId, int durationMinutes, OffsetDateTime now) {
		return create(sessionId, ReportStatus.GENERATING, durationMinutes, now);
	}

	/** 내 말이 너무 적어서 만들지 않는 리포트예요. */
	public static Report none(UUID sessionId, int durationMinutes, OffsetDateTime now) {
		return create(sessionId, ReportStatus.NONE, durationMinutes, now);
	}

	private static Report create(UUID sessionId, ReportStatus status, int durationMinutes, OffsetDateTime now) {
		Report report = new Report();
		report.sessionId = sessionId;
		report.status = status;
		report.durationMinutes = durationMinutes;
		report.createdAt = now;
		report.updatedAt = now;
		return report;
	}

	public void complete(ReportContent content, OffsetDateTime now) {
		this.status = ReportStatus.READY;
		this.failureCode = null;
		this.pastConcern = content.pastConcern();
		this.presentChange = content.presentChange();
		this.oneLineSummary = content.oneLineSummary();
		this.updatedAt = now;
	}

	public void fail(String failureCode, OffsetDateTime now) {
		this.status = ReportStatus.FAILED;
		this.failureCode = failureCode;
		this.updatedAt = now;
	}

	/** 실패한 리포트를 다시 만들어요(끝내기를 다시 불렀을 때). */
	public void retry(OffsetDateTime now) {
		this.status = ReportStatus.GENERATING;
		this.failureCode = null;
		this.updatedAt = now;
	}

	/** 만드는 중인데 너무 오래됐어요(서버가 다시 켜져서 작업이 사라진 경우 포함). */
	public boolean isStale(Duration after) {
		return status == ReportStatus.GENERATING && updatedAt.isBefore(Kst.now().minus(after));
	}
}
