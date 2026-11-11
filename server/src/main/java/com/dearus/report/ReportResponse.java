package com.dearus.report;

import com.dearus.chat.ChatMode;
import com.dearus.chat.ChatSession;
import com.dearus.common.Kst;
import com.dearus.persona.PersonaScope;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * API 명세의 Report. 개인 모드는 pastConcern · presentChange · oneLineSummary가 있고 memorySummary는 null이에요.
 * 커플 모드는 memorySummary(그날의 연인에게서 들은 이야기)와 oneLineSummary가 있고 pastConcern · presentChange는 null이에요.
 */
public record ReportResponse(
	UUID id,
	UUID chatId,
	ChatMode mode,
	PersonaScope scope,
	LocalDate anchorDate,
	int durationMinutes,
	String pastConcern,
	String presentChange,
	String oneLineSummary,
	String memorySummary,
	OffsetDateTime createdAt) {

	public static ReportResponse from(ChatSession session, Report report) {
		return new ReportResponse(
			report.getId(),
			session.getId(),
			session.getCoupleId() == null ? ChatMode.PERSONAL : ChatMode.COUPLE,
			session.scope(),
			session.getAnchorDate(),
			report.getDurationMinutes(),
			report.getPastConcern(),
			report.getPresentChange(),
			report.getOneLineSummary(),
			report.getMemorySummary(),
			Kst.toKst(report.getCreatedAt()));
	}
}
