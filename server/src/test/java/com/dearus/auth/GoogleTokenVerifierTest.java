package com.dearus.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** 네트워크 없이, 시험용 RSA 키로 서명한 JWT로 구글 ID token 검증 규칙(서명 · 발급자 · 대상 · 만료 · sub)을 확인해요. */
class GoogleTokenVerifierTest {

	private static final String CLIENT_ID = "web-client.apps.googleusercontent.com";
	private static final String OTHER_CLIENT_ID = "ios-client.apps.googleusercontent.com";

	private KeyPair pair;
	private RSAPrivateKey privateKey;
	private GoogleTokenVerifier verifier;

	@BeforeEach
	void setUp() throws NoSuchAlgorithmException {
		pair = newKeyPair();
		privateKey = (RSAPrivateKey) pair.getPrivate();
		verifier = new GoogleTokenVerifier(List.of(CLIENT_ID, OTHER_CLIENT_ID), NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build());
	}

	private static KeyPair newKeyPair() throws NoSuchAlgorithmException {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		return generator.generateKeyPair();
	}

	/** 기본값은 정상 토큰이에요. 시험마다 필요한 부분만 바꿔요. */
	private JWTClaimsSet.Builder claims() {
		return new JWTClaimsSet.Builder().issuer("https://accounts.google.com").subject("1234567890").audience(CLIENT_ID).claim("name", "하루")
			.issueTime(Date.from(Instant.now())).expirationTime(Date.from(Instant.now().plus(Duration.ofHours(1))));
	}

	private String sign(JWTClaimsSet claims, RSAPrivateKey key) throws JOSEException {
		SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims);
		jwt.sign(new RSASSASigner(key));
		return jwt.serialize();
	}

	private String sign(JWTClaimsSet.Builder claims) throws JOSEException {
		return sign(claims.build(), privateKey);
	}

	private void assertReason(String token, AuthProviderException.Reason reason) {
		assertThatThrownBy(() -> verifier.verify(token)).isInstanceOfSatisfying(AuthProviderException.class, e -> assertThat(e.reason()).isEqualTo(reason));
	}

	// ───────── 정상 ─────────

	@Test
	void 정상_토큰은_sub와_이름을_돌려줘요() throws Exception {
		VerifiedIdentity identity = verifier.verify(sign(claims()));

		assertThat(identity.providerUserId()).isEqualTo("1234567890");
		assertThat(identity.displayName()).isEqualTo("하루");
	}

	@Test
	void name이_없으면_이름은_null이에요() throws Exception {
		VerifiedIdentity identity = verifier.verify(sign(claims().claim("name", null)));

		assertThat(identity.providerUserId()).isEqualTo("1234567890");
		assertThat(identity.displayName()).isNull();
	}

	@Test
	void 설정한_클라이언트_ID_중_어느_것이_대상이어도_받아요() throws Exception {
		assertThat(verifier.verify(sign(claims().audience(OTHER_CLIENT_ID))).providerUserId()).isEqualTo("1234567890");
		assertThat(verifier.verify(sign(claims().audience(List.of("다른-앱", CLIENT_ID)))).providerUserId()).isEqualTo("1234567890");
	}

	@Test
	void 발급자가_스킴_없는_accounts_google_com이어도_받아요() throws Exception {
		// 구글 문서는 두 가지 값을 모두 허용해요
		assertThat(verifier.verify(sign(claims().issuer("accounts.google.com"))).providerUserId()).isEqualTo("1234567890");
	}

	// ───────── 토큰 문제 → INVALID ─────────

	@Test
	void 우리_클라이언트가_아닌_대상은_거절해요() throws Exception {
		assertReason(sign(claims().audience("남의-앱.apps.googleusercontent.com")), AuthProviderException.Reason.INVALID);
	}

	@Test
	void 구글이_아닌_발급자는_거절해요() throws Exception {
		assertReason(sign(claims().issuer("https://evil.example.com")), AuthProviderException.Reason.INVALID);
		assertReason(sign(claims().issuer("https://accounts.google.com.evil.example.com")), AuthProviderException.Reason.INVALID);
	}

	@Test
	void 만료된_토큰은_거절하고_1분_안의_시계_차이는_봐줘요() throws Exception {
		// 발급(iat)이 만료(exp)보다 먼저여야 올바른 토큰이라서, 발급 시각을 2시간 전으로 둬요
		Date issued = Date.from(Instant.now().minus(Duration.ofHours(2)));
		assertReason(sign(claims().issueTime(issued).expirationTime(Date.from(Instant.now().minus(Duration.ofMinutes(5))))), AuthProviderException.Reason.INVALID);
		assertThat(verifier.verify(sign(claims().issueTime(issued).expirationTime(Date.from(Instant.now().minusSeconds(20))))).providerUserId()).isEqualTo("1234567890");
	}

	@Test
	void 다른_키로_서명한_토큰은_거절해요() throws Exception {
		RSAPrivateKey attacker = (RSAPrivateKey) newKeyPair().getPrivate();

		assertReason(sign(claims().build(), attacker), AuthProviderException.Reason.INVALID);
	}

	@Test
	void 서명이_없는_토큰과_JWT가_아닌_글자는_거절해요() {
		String unsigned = new PlainJWT(claims().build()).serialize();

		assertReason(unsigned, AuthProviderException.Reason.INVALID);
		assertReason("abc", AuthProviderException.Reason.INVALID);
		assertReason("a.b.c", AuthProviderException.Reason.INVALID);
	}

	@Test
	void sub가_없으면_거절해요() throws Exception {
		assertReason(sign(claims().subject(null)), AuthProviderException.Reason.INVALID);
	}

	@Test
	void 오류_메시지에_토큰_글자를_담지_않아요() throws Exception {
		String token = sign(claims().audience("남의-앱"));

		assertThatThrownBy(() -> verifier.verify(token)).hasMessageNotContaining(token);
	}

	// ───────── 제공자 장애 → UNAVAILABLE ─────────

	@Test
	void 구글_공개키를_가져오지_못하면_토큰_문제가_아니라_503_장애예요() throws Exception {
		// 아무도 듣지 않는 주소라 연결이 바로 거절돼요
		GoogleTokenVerifier unreachable = new GoogleTokenVerifier(List.of(CLIENT_ID), NimbusJwtDecoder.withJwkSetUri("http://127.0.0.1:1/jwks").build());

		assertThatThrownBy(() -> unreachable.verify(sign(claims()))).isInstanceOfSatisfying(AuthProviderException.class,
			e -> assertThat(e.reason()).isEqualTo(AuthProviderException.Reason.UNAVAILABLE));
	}

	// ───────── 설정 ─────────

	@Test
	void 클라이언트_ID가_없으면_설정되지_않은_거예요() {
		GoogleTokenVerifier none = new GoogleTokenVerifier(List.of(), NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build());

		assertThat(none.isConfigured()).isFalse();
		assertThat(verifier.isConfigured()).isTrue();
		assertThat(verifier.provider()).isEqualTo("google");
	}
}
