package com.dearus.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtServiceTest {

	private static final String SECRET = "test-secret-test-secret-test-secret-0123";

	private final JwtProperties props = new JwtProperties(SECRET, 14);
	private final JwtConfig config = new JwtConfig();
	private final JwtService service = new JwtService(config.jwtEncoder(props), props);
	private final JwtDecoder decoder = config.jwtDecoder(props);

	@Test
	void 발급한_토큰에는_사용자_ID와_14일_만료가_들어_있어요() {
		UUID userId = UUID.randomUUID();

		JwtService.IssuedToken issued = service.issue(userId);
		Jwt jwt = decoder.decode(issued.value());

		assertThat(jwt.getSubject()).isEqualTo(userId.toString());
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("dearus");
		Duration lifetime = Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt());
		assertThat(lifetime).isEqualTo(Duration.ofDays(14));
		assertThat(jwt.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofDays(13)));
	}

	@Test
	void 응답에_담는_만료_시각은_한국_시간이에요() {
		JwtService.IssuedToken issued = service.issue(UUID.randomUUID());

		assertThat(issued.expiresAt().getOffset().getTotalSeconds()).isEqualTo(9 * 3600);
	}

	@Test
	void 다른_비밀_값으로_서명한_토큰은_거절해요() {
		JwtProperties other = new JwtProperties("another-secret-another-secret-another-9876", 14);
		JwtService otherService = new JwtService(config.jwtEncoder(other), other);
		String forged = otherService.issue(UUID.randomUUID()).value();

		assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
	}

	@Test
	void 엉터리_문자열은_거절해요() {
		assertThatThrownBy(() -> decoder.decode("not-a-jwt")).isInstanceOf(JwtException.class);
	}

	@Test
	void 비밀_값이_32자보다_짧으면_서버가_시작되지_않아요() {
		JwtProperties tooShort = new JwtProperties("short", 14);

		assertThatThrownBy(() -> config.jwtEncoder(tooShort))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("JWT_SECRET");
	}
}
