package com.dearus.persona;

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
 * persona_analyses 테이블 (ERD). 한 시점의 분석 결과를 암호화해서 담아요.
 * 같은 시점(user_id, anchor_date, window_days)은 한 행이고, 기록이 바뀌어 다시 분석하면 {@link #replace}로 덮어써요.
 */
@Entity
@Table(name = "persona_analyses")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonaAnalysis {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "anchor_date", nullable = false, updatable = false)
	private LocalDate anchorDate;

	@Column(name = "window_days", nullable = false, updatable = false)
	private short windowDays;

	@Column(name = "analysis_enc", nullable = false)
	private byte[] analysisEnc;

	@Column(name = "source_digest", nullable = false, length = 64)
	private String sourceDigest;

	@Column(name = "diary_count", nullable = false)
	private int diaryCount;

	@Column(name = "messenger_weeks", nullable = false)
	private int messengerWeeks;

	@Column(nullable = false, length = 60)
	private String model;

	@Column(name = "analyzed_at", nullable = false)
	private OffsetDateTime analyzedAt;

	public static PersonaAnalysis create(UUID userId, LocalDate anchorDate, PersonaScope scope, byte[] analysisEnc,
		String sourceDigest, int diaryCount, int messengerWeeks, String model) {
		PersonaAnalysis row = new PersonaAnalysis();
		row.userId = userId;
		row.anchorDate = anchorDate;
		row.windowDays = (short) scope.windowDays();
		row.replace(analysisEnc, sourceDigest, diaryCount, messengerWeeks, model);
		return row;
	}

	/** 기록이 바뀌어 다시 분석한 결과로 덮어써요. */
	public void replace(byte[] analysisEnc, String sourceDigest, int diaryCount, int messengerWeeks, String model) {
		this.analysisEnc = analysisEnc;
		this.sourceDigest = sourceDigest;
		this.diaryCount = diaryCount;
		this.messengerWeeks = messengerWeeks;
		this.model = model;
		this.analyzedAt = Kst.now();
	}
}
