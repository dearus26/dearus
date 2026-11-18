package com.dearus.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * API 명세의 UserConsentRequest. 서비스 이용 동의 화면의 체크 항목이에요.
 * 필수 4개(terms, privacy, aiAnalysis, sensitiveInfo)는 모두 true여야 하고, notifications(선택)는 안 보내면 그대로예요.
 * birthDate는 저장된 생년월일이 없을 때만 꼭 필요해요(검사는 {@link ConsentService}).
 */
public record ConsentRequest(
	@NotNull(message = "꼭 필요해요") @AssertTrue(message = "필수 항목이에요") Boolean terms,
	@NotNull(message = "꼭 필요해요") @AssertTrue(message = "필수 항목이에요") Boolean privacy,
	@NotNull(message = "꼭 필요해요") @AssertTrue(message = "필수 항목이에요") Boolean aiAnalysis,
	@NotNull(message = "꼭 필요해요") @AssertTrue(message = "필수 항목이에요") Boolean sensitiveInfo,
	LocalDate birthDate,
	Boolean notifications) {
}
