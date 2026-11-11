package com.dearus.couple;

import com.dearus.persona.PersonaScope;
import java.time.LocalDate;
import java.util.UUID;

/**
 * API 명세의 CoupleComparison. partnerPastSelf는 내가 그날의 연인에게서 들은 이야기(내 리포트),
 * myPastSelf는 연인이 그날의 나에게서 들은 이야기(연인 리포트)예요. 두 요약은 내가 대화를 끝낸 뒤에만 보여요.
 */
public record CoupleComparisonResponse(
	String status,
	LocalDate anchorDate,
	PersonaScope scope,
	UUID chatId,
	String partnerNickname,
	PastSelf partnerPastSelf,
	PastSelf myPastSelf,
	String gapSummary) {

	public record PastSelf(String memorySummary) {
	}
}
