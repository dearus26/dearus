package com.dearus.auth;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 구글 ID token(JWT)을 확인해요. 구글 공개키(JWKS)로 서명을 확인하고, 발급자(iss) · 대상(aud, 우리 클라이언트 ID 목록) · 만료(exp)를 봐요.
 * 사용자 구분 값은 sub이고(이메일은 바뀔 수 있어서 쓰지 않아요), 이름은 name 클레임이 있을 때만 써요.
 * 공개키는 구글이 가끔 바꿔서 라이브러리가 캐시해 두고 필요하면 다시 가져와요. 가져오지 못하면 토큰 문제가 아니라 제공자 장애(503)예요.
 */
@Component
public class GoogleTokenVerifier implements SocialTokenVerifier {

	private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);

	static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
	/** 구글 문서가 알려주는 발급자 값 두 가지 */
	static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

	private final List<String> clientIds;
	private final NimbusJwtDecoder decoder;

	@Autowired
	public GoogleTokenVerifier(AuthProperties props) {
		this(props.google().clientIds(), jwksDecoder());
	}

	/** 시험에서 공개키를 직접 넣을 수 있게 열어 둔 생성자예요. 검증 규칙은 여기서 똑같이 붙여요. */
	GoogleTokenVerifier(List<String> clientIds, NimbusJwtDecoder decoder) {
		this.clientIds = List.copyOf(clientIds);
		this.decoder = decoder;
		this.decoder.setJwtValidator(validator(this.clientIds));
	}

	private static NimbusJwtDecoder jwksDecoder() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(3));
		factory.setReadTimeout(Duration.ofSeconds(5));
		return NimbusJwtDecoder.withJwkSetUri(JWKS_URI).jwsAlgorithm(SignatureAlgorithm.RS256).restOperations(new RestTemplate(factory)).build();
	}

	static OAuth2TokenValidator<Jwt> validator(List<String> clientIds) {
		return new DelegatingOAuth2TokenValidator<>(
			new JwtTimestampValidator(Duration.ofSeconds(60)),
			new JwtClaimValidator<Object>(JwtClaimNames.ISS, iss -> iss != null && ISSUERS.contains(iss.toString())),
			new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.stream().anyMatch(clientIds::contains)));
	}

	@Override
	public String provider() {
		return "google";
	}

	@Override
	public boolean isConfigured() {
		return !clientIds.isEmpty();
	}

	@Override
	public VerifiedIdentity verify(String token) {
		Jwt jwt;
		try {
			jwt = decoder.decode(token);
		} catch (BadJwtException e) {
			// 서명 · 형식 · 발급자 · 대상 · 만료가 맞지 않아요. 토큰 글자는 남기지 않고 이유만 남겨요.
			log.warn("구글 토큰이 올바르지 않아요: {}", e.getMessage());
			throw new AuthProviderException(AuthProviderException.Reason.INVALID, "구글 토큰이 올바르지 않아요", e);
		} catch (JwtException e) {
			log.error("구글 공개키를 확인하지 못했어요: {}", e.getMessage());
			throw new AuthProviderException(AuthProviderException.Reason.UNAVAILABLE, "구글에 연결하지 못했어요", e);
		}
		String sub = jwt.getSubject();
		if (sub == null || sub.isBlank()) {
			throw new AuthProviderException(AuthProviderException.Reason.INVALID, "구글 토큰에 사용자 ID가 없어요");
		}
		return new VerifiedIdentity(sub, jwt.getClaimAsString("name"));
	}
}
