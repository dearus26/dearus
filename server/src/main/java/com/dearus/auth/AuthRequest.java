package com.dearus.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** API 명세의 AuthRequest. token은 소셜 SDK가 준 토큰이에요 (개발용 로그인에서는 아무 문자열). */
public record AuthRequest(
	@NotBlank(message = "꼭 필요해요") @Size(max = 255, message = "255자까지 쓸 수 있어요") String token,
	@Size(max = 20, message = "20자까지 쓸 수 있어요") String nickname) {
}
