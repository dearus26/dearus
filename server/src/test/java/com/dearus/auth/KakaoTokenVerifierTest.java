package com.dearus.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 카카오 서버 없이, 가짜 서버로 카카오 access token 확인(app_id 일치 · 사용자 정보 · 오류 구분)을 확인해요. */
class KakaoTokenVerifierTest {

	private static final String TOKEN_INFO = KakaoTokenVerifier.BASE_URL + "/v1/user/access_token_info";
	private static final String USER_ME = KakaoTokenVerifier.BASE_URL + "/v2/user/me";

	private MockRestServiceServer server;
	private KakaoTokenVerifier verifier;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl(KakaoTokenVerifier.BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
		verifier = new KakaoTokenVerifier(builder.build(), "123456");
	}

	private void tokenInfo(String json) {
		server.expect(requestTo(TOKEN_INFO)).andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Bearer kakao-token"))
			.andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
	}

	private void userMe(String json) {
		server.expect(requestTo(USER_ME)).andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Bearer kakao-token"))
			.andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
	}

	private void assertReason(String token, AuthProviderException.Reason reason) {
		assertThatThrownBy(() -> verifier.verify(token)).isInstanceOfSatisfying(AuthProviderException.class, e -> assertThat(e.reason()).isEqualTo(reason));
	}

	// ───────── 정상 ─────────

	@Test
	void 우리_앱의_토큰이면_id와_닉네임을_돌려줘요() {
		tokenInfo("{\"id\": 111, \"expires_in\": 3000, \"app_id\": 123456}");
		userMe("{\"id\": 111, \"kakao_account\": {\"profile\": {\"nickname\": \"하루\"}}}");

		VerifiedIdentity identity = verifier.verify("kakao-token");

		assertThat(identity.providerUserId()).isEqualTo("111");
		assertThat(identity.displayName()).isEqualTo("하루");
		server.verify();
	}

	@Test
	void 닉네임에_동의하지_않았으면_이름은_null이에요() {
		tokenInfo("{\"id\": 111, \"expires_in\": 3000, \"app_id\": 123456}");
		userMe("{\"id\": 111, \"kakao_account\": {\"profile_nickname_needs_agreement\": true}}");

		VerifiedIdentity identity = verifier.verify("kakao-token");

		assertThat(identity.providerUserId()).isEqualTo("111");
		assertThat(identity.displayName()).isNull();
	}

	@Test
	void 아주_큰_id도_글자_그대로_돌려줘요() {
		tokenInfo("{\"id\": 4123456789012345678, \"expires_in\": 3000, \"app_id\": 123456}");
		userMe("{\"id\": 4123456789012345678}");

		assertThat(verifier.verify("kakao-token").providerUserId()).isEqualTo("4123456789012345678");
	}

	// ───────── 토큰 문제 → INVALID ─────────

	@Test
	void 다른_카카오_앱에서_발급된_토큰은_거절하고_사용자_정보는_묻지_않아요() {
		tokenInfo("{\"id\": 111, \"expires_in\": 3000, \"app_id\": 999999}");

		assertReason("kakao-token", AuthProviderException.Reason.INVALID);

		server.verify(); // 두 번째 요청(user/me)을 기대하지 않았고 실제로도 부르지 않았어요
	}

	@Test
	void app_id가_없거나_숫자가_아니면_거절해요() {
		tokenInfo("{\"id\": 111, \"expires_in\": 3000}");
		assertReason("kakao-token", AuthProviderException.Reason.INVALID);

		server.reset();
		tokenInfo("{\"id\": 111, \"expires_in\": 3000, \"app_id\": \"123456\"}");
		assertReason("kakao-token", AuthProviderException.Reason.INVALID);
	}

	@Test
	void 카카오가_401_400_403을_주면_토큰_문제예요() {
		for (HttpStatus status : new HttpStatus[] {HttpStatus.UNAUTHORIZED, HttpStatus.BAD_REQUEST, HttpStatus.FORBIDDEN}) {
			server.reset();
			server.expect(requestTo(TOKEN_INFO)).andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON)
				.body("{\"msg\": \"this access token does not exist\", \"code\": -401}"));

			assertReason("kakao-token", AuthProviderException.Reason.INVALID);
		}
	}

	@Test
	void 사용자_정보에서_401이어도_토큰_문제예요() {
		tokenInfo("{\"id\": 111, \"expires_in\": 3000, \"app_id\": 123456}");
		server.expect(requestTo(USER_ME)).andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body("{\"code\": -401}"));

		assertReason("kakao-token", AuthProviderException.Reason.INVALID);
	}

	@Test
	void 헤더에_넣을_수_없는_글자가_든_토큰은_카카오에_보내지_않고_거절해요() {
		assertReason("bad token", AuthProviderException.Reason.INVALID);
		assertReason("line1\r\nX-Injected: 1", AuthProviderException.Reason.INVALID);
		assertReason("한글토큰", AuthProviderException.Reason.INVALID);

		server.verify(); // 어떤 요청도 나가지 않았어요
	}

	// ───────── 제공자 장애 → UNAVAILABLE ─────────

	@Test
	void 카카오_서버_오류와_호출_제한은_장애예요() {
		for (HttpStatus status : new HttpStatus[] {HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.BAD_GATEWAY, HttpStatus.SERVICE_UNAVAILABLE,
			HttpStatus.TOO_MANY_REQUESTS}) {
			server.reset();
			server.expect(requestTo(TOKEN_INFO)).andRespond(withStatus(status));

			assertReason("kakao-token", AuthProviderException.Reason.UNAVAILABLE);
		}
	}

	@Test
	void 연결이_끊기면_장애예요() {
		server.expect(requestTo(TOKEN_INFO)).andRespond(request -> {
			throw new IOException("연결 끊김");
		});

		assertReason("kakao-token", AuthProviderException.Reason.UNAVAILABLE);
	}

	@Test
	void JSON이_아니거나_id가_없는_응답은_장애예요() {
		server.expect(requestTo(TOKEN_INFO)).andRespond(withSuccess("<html>점검 중</html>", MediaType.TEXT_HTML));
		assertReason("kakao-token", AuthProviderException.Reason.UNAVAILABLE);

		server.reset();
		tokenInfo("{\"id\": 111, \"expires_in\": 3000, \"app_id\": 123456}");
		userMe("{\"kakao_account\": {}}");
		assertReason("kakao-token", AuthProviderException.Reason.UNAVAILABLE);
	}

	// ───────── 설정 ─────────

	@Test
	void 앱_ID가_숫자일_때만_설정된_거예요() {
		RestClient rest = RestClient.builder().baseUrl(KakaoTokenVerifier.BASE_URL).build();

		assertThat(new KakaoTokenVerifier(rest, "123456").isConfigured()).isTrue();
		assertThat(new KakaoTokenVerifier(rest, " 123456 ").isConfigured()).isTrue();
		assertThat(new KakaoTokenVerifier(rest, "").isConfigured()).isFalse();
		assertThat(new KakaoTokenVerifier(rest, null).isConfigured()).isFalse();
		assertThat(new KakaoTokenVerifier(rest, "abc123").isConfigured()).isFalse();
		assertThat(new KakaoTokenVerifier(rest, "123456").provider()).isEqualTo("kakao");
	}
}
