package com.dearus.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * API 명세의 AuthRequest. token은 소셜 SDK가 준 토큰이에요 (개발용 로그인에서는 아무 문자열).
 * 구글 ID token이나 애플 identity token(JWT)은 보통 1,000자 안팎이라 넉넉하게 {@link #TOKEN_MAX}자까지 받아요.
 */
public record AuthRequest(
	@NotBlank(message = "꼭 필요해요") @Size(max = AuthRequest.TOKEN_MAX, message = "4096자까지 쓸 수 있어요") String token,
	@Size(max = 20, message = "20자까지 쓸 수 있어요") String nickname) {

	public static final int TOKEN_MAX = 4096;
}
