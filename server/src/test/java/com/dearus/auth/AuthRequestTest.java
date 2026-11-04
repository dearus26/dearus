package com.dearus.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * 로그인 요청의 입력 검증을 확인해요. 구글 ID token이나 애플 identity token(JWT)은 1,000자 안팎이라서,
 * 토큰 길이 제한이 너무 짧으면 진짜 소셜 토큰이 전부 400으로 거절돼요(처음에 255자로 막아 뒀다가 서버를 켜 보고 찾았어요).
 */
class AuthRequestTest {

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	private Set<String> invalidFields(AuthRequest request) {
		return validator.validate(request).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
	}

	@Test
	void 구글_ID_token_정도로_긴_토큰도_받아요() {
		assertThat(invalidFields(new AuthRequest("a".repeat(1100), null))).isEmpty();
		assertThat(invalidFields(new AuthRequest("a".repeat(AuthRequest.TOKEN_MAX), null))).isEmpty();
	}

	@Test
	void 너무_긴_토큰과_빈_토큰은_거절해요() {
		assertThat(invalidFields(new AuthRequest("a".repeat(AuthRequest.TOKEN_MAX + 1), null))).containsExactly("token");
		assertThat(invalidFields(new AuthRequest("", null))).containsExactly("token");
		assertThat(invalidFields(new AuthRequest("   ", null))).containsExactly("token");
		assertThat(invalidFields(new AuthRequest(null, null))).containsExactly("token");
	}

	@Test
	void 닉네임은_20자까지예요() {
		assertThat(invalidFields(new AuthRequest("t", "가".repeat(20)))).isEmpty();
		assertThat(invalidFields(new AuthRequest("t", "가".repeat(21)))).containsExactly("nickname");
		assertThat(invalidFields(new AuthRequest("t", null))).isEmpty();
	}
}
