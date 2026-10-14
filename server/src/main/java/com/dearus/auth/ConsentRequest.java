package com.dearus.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * API 명세의 ConsentRequest. 서비스 이용 동의 화면의 체크 항목이에요.
 * 필수 3개(terms, privacy, aiAnalysis)는 모두 true여야 하고, notifications(선택)는 안 보내면 그대로예요.
 */
public record ConsentRequest(
	@NotNull(message = "꼭 필요해요") @AssertTrue(message = "필수 항목이에요") Boolean terms,
	@NotNull(message = "꼭 필요해요") @AssertTrue(message = "필수 항목이에요") Boolean privacy,
	@NotNull(message = "꼭 필요해요") @AssertTrue(message = "필수 항목이에요") Boolean aiAnalysis,
	Boolean notifications) {
}
