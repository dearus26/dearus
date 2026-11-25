package com.dearus.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dearus.auth.JwtProperties;
import com.dearus.auth.JwtService;
import com.dearus.common.ApiException;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** 관리자 로그인과 관리자 토큰이 앱 사용자 토큰과 서로 바꿔 쓰이지 않는지 확인해요(2026.11.25). */
class AdminAuthServiceTest {

	private static final String JWT_SECRET = "test-jwt-secret-test-jwt-secret-1234";
	private static final String PASSWORD = "admin-password-for-tests";
	private final JwtProperties jwtProps = new JwtProperties(JWT_SECRET, 14);

	private AdminAuthService service(String password) {
		AdminProperties props = new AdminProperties(password, 8);
		return new AdminAuthService(props, new AdminTokens(props, jwtProps));
	}

	/** 앱 API가 쓰는 디코더와 같아요(JwtConfig): JWT_SECRET 키, 발급자 dearus */
	private NimbusJwtDecoder userDecoder() {
		NimbusJwtDecoder decoder = NimbusJwtDecoder
			.withSecretKey(new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
			.macAlgorithm(MacAlgorithm.HS256).build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("dearus"));
		return decoder;
	}

	@Test
	void 비밀번호가_맞으면_관리자_토큰을_받아요() {
		AdminProperties props = new AdminProperties(PASSWORD, 8);
		AdminTokens tokens = new AdminTokens(props, jwtProps);
		AdminTokens.IssuedToken token = new AdminAuthService(props, tokens).login(PASSWORD);

		assertThat(tokens.decode(token.value()).getSubject()).isEqualTo("admin");
		assertThat(token.expiresAt()).isNotNull();
	}

	@Test
	void 비밀번호가_틀리면_401이에요() {
		assertThatThrownBy(() -> service(PASSWORD).login(PASSWORD + "x"))
			.isInstanceOfSatisfying(ApiException.class, e -> {
				assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
				assertThat(e.getCode()).isEqualTo("ADMIN_LOGIN_FAILED");
			});
	}

	@Test
	void 비밀번호를_넣지_않으면_관리자_로그인이_꺼져요() {
		assertThatThrownBy(() -> service("").login("anything-anything"))
			.isInstanceOfSatisfying(ApiException.class, e -> {
				assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
				assertThat(e.getCode()).isEqualTo("ADMIN_DISABLED");
			});
	}

	@Test
	void 짧은_비밀번호로는_서버가_켜지지_않아요() {
		assertThatThrownBy(() -> new AdminProperties("short", 8)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 관리자_토큰으로는_앱_API를_못_써요() {
		AdminProperties props = new AdminProperties(PASSWORD, 8);
		String adminToken = new AdminTokens(props, jwtProps).issue().value();

		assertThatThrownBy(() -> userDecoder().decode(adminToken)).isInstanceOf(JwtException.class);
	}

	@Test
	void 앱_사용자_토큰으로는_관리자_API를_못_써요() {
		NimbusJwtEncoder userEncoder = new NimbusJwtEncoder(
			new ImmutableSecret<>(new SecretKeySpec(JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
		String userToken = new JwtService(userEncoder, jwtProps).issue(UUID.randomUUID()).value();
		AdminTokens tokens = new AdminTokens(new AdminProperties(PASSWORD, 8), jwtProps);

		assertThat(userDecoder().decode(userToken).getClaimAsString("iss")).isEqualTo("dearus");
		assertThatThrownBy(() -> tokens.decode(userToken)).isInstanceOf(JwtException.class);
	}

	@Test
	void 비밀번호를_바꾸면_예전_관리자_토큰은_못_써요() {
		String oldToken = new AdminTokens(new AdminProperties(PASSWORD, 8), jwtProps).issue().value();
		AdminTokens changed = new AdminTokens(new AdminProperties(PASSWORD + "-new", 8), jwtProps);

		assertThatThrownBy(() -> changed.decode(oldToken)).isInstanceOf(JwtException.class);
	}

	@Test
	void 관리자_로그인이_꺼져_있으면_어떤_토큰도_받지_않아요() {
		String token = new AdminTokens(new AdminProperties(PASSWORD, 8), jwtProps).issue().value();
		AdminTokens disabled = new AdminTokens(new AdminProperties(null, 8), jwtProps);

		assertThatThrownBy(() -> disabled.decode(token)).isInstanceOf(JwtException.class);
	}
}
