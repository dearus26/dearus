package com.dearus.admin;

import com.dearus.auth.JwtProperties;
import com.dearus.common.Kst;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * 관리자 토큰(JWT)을 만들고 확인해요. 앱 사용자 토큰과 서로 바꿔 쓸 수 없게 서명 키를 따로 둬요:
 * 키는 JWT_SECRET과 ADMIN_PASSWORD로 만들어서(HMAC-SHA256), 비밀번호를 바꾸면 이미 받은 관리자 토큰도 모두 못 쓰게 돼요.
 * 발급자도 사용자 토큰(dearus)과 달라요(dearus-admin).
 * 인코더 · 디코더를 Spring 빈으로 두지 않아요. 빈이 두 개가 되면 사용자 로그인 쪽(JwtService, 기본 보안 설정)이 어느 것을 쓸지 헷갈려요.
 */
@Component
public class AdminTokens {

	static final String ISSUER = "dearus-admin";
	static final String SUBJECT = "admin";

	public record IssuedToken(String value, OffsetDateTime expiresAt) {
	}

	private final AdminProperties props;
	private final NimbusJwtEncoder encoder;
	private final NimbusJwtDecoder decoder;

	public AdminTokens(AdminProperties props, JwtProperties jwt) {
		this.props = props;
		SecretKey key = adminKey(jwt.secret(), props.password());
		this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
		this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
		this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
			JwtValidators.createDefaultWithIssuer(ISSUER),
			token -> SUBJECT.equals(token.getSubject())
				? OAuth2TokenValidatorResult.success()
				: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "관리자 토큰이 아니에요", null))));
	}

	static SecretKey adminKey(String jwtSecret, String adminPassword) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] key = mac.doFinal(("dearus-admin:" + (adminPassword == null ? "" : adminPassword)).getBytes(StandardCharsets.UTF_8));
			return new SecretKeySpec(key, "HmacSHA256");
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("관리자 토큰 키를 만들지 못했어요", e);
		}
	}

	IssuedToken issue() {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(props.tokenHours(), ChronoUnit.HOURS);
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(ISSUER)
			.subject(SUBJECT)
			.issuedAt(now)
			.expiresAt(expiresAt)
			.build();
		String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
		return new IssuedToken(token, expiresAt.atZone(Kst.ZONE).toOffsetDateTime());
	}

	/** 관리자 API의 토큰 확인에 써요. 관리자 로그인이 꺼져 있으면 어떤 토큰도 받지 않아요. */
	JwtDecoder decoder() {
		return token -> {
			if (!props.enabled()) {
				throw new BadJwtException("관리자 로그인이 꺼져 있어요");
			}
			return decoder.decode(token);
		};
	}

	/** 시험용: 토큰을 확인해서 돌려줘요. */
	Jwt decode(String token) {
		return decoder().decode(token);
	}
}
