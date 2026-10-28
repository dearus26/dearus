package com.dearus.chat;

import com.dearus.common.Kst;
import com.dearus.persona.PersonaAnalysisService;
import com.dearus.persona.PersonaRange;
import com.dearus.persona.PersonaScope;
import com.dearus.persona.RecordLevel;
import com.dearus.persona.ShortcutKey;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * API 명세의 ChatSession. windowStart · windowEnd는 세션을 만든 날짜를 기준으로 다시 계산한 값이고(저장하지 않아요),
 * preparingStep은 PREPARING일 때만 있고 서버가 단계를 모르면(다시 켜진 경우 등) null이에요.
 */
public record ChatSessionResponse(
	UUID id,
	ChatStatus status,
	PersonaAnalysisService.Step preparingStep,
	String failureCode,
	ChatMode mode,
	UUID coupleId,
	PersonaScope scope,
	ShortcutKey shortcutKey,
	LocalDate anchorDate,
	LocalDate windowStart,
	LocalDate windowEnd,
	RecordLevel level,
	OffsetDateTime startedAt,
	OffsetDateTime endedAt) {

	public static ChatSessionResponse from(ChatSession session, PersonaAnalysisService.Step preparingStep) {
		PersonaRange range = PersonaRange.at(session.scope(), session.getAnchorDate(),
			session.getStartedAt().atZoneSameInstant(Kst.ZONE).toLocalDate());
		return new ChatSessionResponse(
			session.getId(),
			session.getStatus(),
			session.isPreparing() ? preparingStep : null,
			session.getFailureCode(),
			session.getCoupleId() == null ? ChatMode.PERSONAL : ChatMode.COUPLE,
			session.getCoupleId(),
			session.scope(),
			session.getShortcutKey(),
			session.getAnchorDate(),
			range.start(),
			range.end(),
			session.getRecordLevel(),
			Kst.toKst(session.getStartedAt()),
			Kst.toKst(session.getEndedAt()));
	}
}
