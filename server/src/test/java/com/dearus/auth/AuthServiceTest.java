package com.dearus.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dearus.common.ApiException;
import com.dearus.common.Kst;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** DB · 제공자 없이 로그인 규칙(방식 고르기 · 응답 코드 · 계정 만들기 · 닉네임 · 동시 가입)을 확인해요. */
class AuthServiceTest {

	/** 시험용 검증기예요. 받은 토큰을 기록하고, 정해 둔 대로 사람을 돌려주거나 오류를 던져요. */
	private static class FakeVerifier implements SocialTokenVerifier {

		private final String provider;
		boolean configured = true;
		Function<String, VerifiedIdentity> behavior = token -> new VerifiedIdentity("social-1", "제공자이름");
		final List<String> tokens = new java.util.ArrayList<>();

		FakeVerifier(String provider) {
			this.provider = provider;
		}

		@Override
		public String provider() {
			return provider;
		}

		@Override
		public boolean isConfigured() {
			return configured;
		}

		@Override
		public VerifiedIdentity verify(String token) {
			tokens.add(token);
			return behavior.apply(token);
		}
	}

	private final UserRepository users = mock(UserRepository.class);
	private final JwtService jwt = mock(JwtService.class);
	private final Map<String, User> store = new HashMap<>(); // "provider|providerUserId" → 사용자
	private final FakeVerifier kakao = new FakeVerifier("kakao");
	private final FakeVerifier google = new FakeVerifier("google");
	private boolean devEnabled = true;
	private AuthService service;

	@BeforeEach
	void setUp() {
		when(jwt.issue(any())).thenReturn(new JwtService.IssuedToken("jwt-value", Kst.now().plusDays(14)));
		when(users.findByProviderAndProviderUserId(any(), any())).thenAnswer(call -> Optional.ofNullable(store.get(call.getArgument(0) + "|" + call.getArgument(1))));
		when(users.saveAndFlush(any())).thenAnswer(call -> {
			User user = call.getArgument(0);
			ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
			store.put(user.getProvider() + "|" + user.getProviderUserId(), user);
			return user;
		});
		service = newService();
	}

	private AuthService newService() {
		return new AuthService(users, jwt, new AuthProperties(devEnabled, null, null), List.of(kakao, google),
			new TransactionTemplate(mock(PlatformTransactionManager.class)));
	}

	private static AuthRequest request(String token, String nickname) {
		return new AuthRequest(token, nickname);
	}

	private void assertApi(Runnable call, HttpStatus status, String code) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
			assertThat(e.getStatus()).isEqualTo(status);
			assertThat(e.getCode()).isEqualTo(code);
		});
	}

	// ───────── 로그인 방식 고르기 ─────────

	@Test
	void 알_수_없는_방식은_404예요() {
		assertApi(() -> service.login("twitter", request("t", null)), HttpStatus.NOT_FOUND, "NOT_FOUND");
	}

	@Test
	void 꺼진_dev_로그인은_없는_방식처럼_404예요() {
		devEnabled = false;
		service = newService();

		assertApi(() -> service.login("dev", request("t", null)), HttpStatus.NOT_FOUND, "NOT_FOUND");
		verify(users, never()).saveAndFlush(any());
	}

	@Test
	void 애플은_아직_501이에요() {
		assertApi(() -> service.login("apple", request("t", null)), HttpStatus.NOT_IMPLEMENTED, "AUTH_PROVIDER_NOT_READY");
	}

	@Test
	void 설정이_없는_제공자는_501이고_검증기를_부르지_않아요() {
		google.configured = false;

		assertApi(() -> service.login("google", request("t", null)), HttpStatus.NOT_IMPLEMENTED, "AUTH_PROVIDER_NOT_READY");

		assertThat(google.tokens).isEmpty();
		verify(users, never()).saveAndFlush(any());
	}

	@Test
	void 제공자_이름은_대소문자를_가리지_않아요() {
		AuthResponse response = service.login("KaKao", request("  t-1  ", null));

		assertThat(response.user().provider()).isEqualTo("kakao");
		assertThat(kakao.tokens).containsExactly("t-1"); // 앞뒤 공백은 떼고 보내요
	}

	// ───────── 소셜 로그인 ─────────

	@Test
	void 처음_로그인하면_계정이_만들어지고_JWT를_돌려줘요() {
		AuthResponse response = service.login("kakao", request("t", null));

		assertThat(response.isNewUser()).isTrue();
		assertThat(response.accessToken()).isEqualTo("jwt-value");
		assertThat(response.tokenType()).isEqualTo("Bearer");
		assertThat(response.user().provider()).isEqualTo("kakao");
		assertThat(response.user().nickname()).isEqualTo("제공자이름");
		assertThat(response.user().needsConsent()).isTrue();
		assertThat(store).containsKey("kakao|social-1");
	}

	@Test
	void 같은_계정으로_다시_로그인하면_새로_만들지_않고_닉네임도_바꾸지_않아요() {
		service.login("kakao", request("t", null));
		kakao.behavior = token -> new VerifiedIdentity("social-1", "바뀐 이름");

		AuthResponse again = service.login("kakao", request("t", "또 다른 이름"));

		assertThat(again.isNewUser()).isFalse();
		assertThat(again.user().nickname()).isEqualTo("제공자이름");
		verify(users, org.mockito.Mockito.times(1)).saveAndFlush(any());
	}

	@Test
	void 카카오와_구글은_같은_사람이어도_별개_계정이에요() {
		kakao.behavior = token -> new VerifiedIdentity("same-person", "하루");
		google.behavior = token -> new VerifiedIdentity("same-person", "하루");

		AuthResponse fromKakao = service.login("kakao", request("t", null));
		AuthResponse fromGoogle = service.login("google", request("t", null));

		assertThat(fromKakao.isNewUser()).isTrue();
		assertThat(fromGoogle.isNewUser()).isTrue();
		assertThat(fromKakao.user().id()).isNotEqualTo(fromGoogle.user().id());
	}

	// ───────── 닉네임 규칙 ─────────

	@Test
	void 닉네임은_앱이_보낸_값_제공자_이름_사용자_순이에요() {
		kakao.behavior = token -> new VerifiedIdentity("a", "제공자이름");
		assertThat(service.login("kakao", request("t", "앱이름")).user().nickname()).isEqualTo("앱이름");

		kakao.behavior = token -> new VerifiedIdentity("b", "제공자이름");
		assertThat(service.login("kakao", request("t", "  ")).user().nickname()).isEqualTo("제공자이름");

		kakao.behavior = token -> new VerifiedIdentity("c", null);
		assertThat(service.login("kakao", request("t", null)).user().nickname()).isEqualTo("사용자");

		kakao.behavior = token -> new VerifiedIdentity("d", " ");
		assertThat(service.login("kakao", request("t", "")).user().nickname()).isEqualTo("사용자");
	}

	@Test
	void 닉네임은_20자까지만_쓰고_이모지를_반으로_자르지_않아요() {
		assertThat(AuthService.nicknameOf("가".repeat(30), null, null)).isEqualTo("가".repeat(20));
		assertThat(AuthService.nicknameOf(null, "나".repeat(20), null)).isEqualTo("나".repeat(20));
		String emojis = AuthService.nicknameOf("😀".repeat(30), null, null);
		assertThat(emojis.codePointCount(0, emojis.length())).isEqualTo(20);
		assertThat(AuthService.nicknameOf("  공백  ", null, null)).isEqualTo("공백");
	}

	@Test
	void dev_로그인은_이름이_없으면_token을_닉네임으로_쓰고_20자로_잘라요() {
		AuthResponse response = service.login("dev", request("dev-user-with-a-very-long-token-name", null));

		assertThat(response.isNewUser()).isTrue();
		assertThat(response.user().provider()).isEqualTo("dev");
		assertThat(response.user().nickname()).isEqualTo("dev-user-with-a-very");
	}

	@Test
	void dev_로그인의_token은_DB_컬럼_길이_255자까지예요() {
		assertThat(service.login("dev", request("d".repeat(255), null)).isNewUser()).isTrue();

		assertApi(() -> service.login("dev", request("d".repeat(256), null)), HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
	}

	@Test
	void 소셜_토큰은_255자보다_길어도_제공자에게_그대로_보내요() {
		String longToken = "j".repeat(1100); // 구글 ID token 정도의 길이

		service.login("google", request(longToken, null));

		assertThat(google.tokens).containsExactly(longToken);
	}

	// ───────── 오류 구분 ─────────

	@Test
	void 토큰이_잘못되면_401이고_계정을_만들지_않아요() {
		kakao.behavior = token -> {
			throw new AuthProviderException(AuthProviderException.Reason.INVALID, "토큰 불량");
		};

		assertApi(() -> service.login("kakao", request("t", null)), HttpStatus.UNAUTHORIZED, "AUTH_PROVIDER_TOKEN_INVALID");

		verify(users, never()).saveAndFlush(any());
		verify(jwt, never()).issue(any());
	}

	@Test
	void 제공자_장애면_503이고_계정을_만들지_않아요() {
		google.behavior = token -> {
			throw new AuthProviderException(AuthProviderException.Reason.UNAVAILABLE, "구글 연결 실패");
		};

		assertApi(() -> service.login("google", request("t", null)), HttpStatus.SERVICE_UNAVAILABLE, "AUTH_PROVIDER_UNAVAILABLE");

		verify(users, never()).saveAndFlush(any());
		verify(jwt, never()).issue(any());
	}

	@Test
	void 오류_응답에_토큰이나_제공자_내부_메시지를_담지_않아요() {
		kakao.behavior = token -> {
			throw new AuthProviderException(AuthProviderException.Reason.INVALID, "내부 상세: " + token);
		};

		assertThatThrownBy(() -> service.login("kakao", request("secret-token", null))).hasMessageNotContaining("secret-token");
	}

	// ───────── 동시 가입 ─────────

	@Test
	void 처음_로그인이_동시에_들어와_다른_쪽이_먼저_만들었으면_그_계정을_써요() {
		User winner = User.create("kakao", "social-1", "먼저 만든 사람");
		ReflectionTestUtils.setField(winner, "id", UUID.randomUUID());
		// 처음 찾을 때는 없고(경합 직전), 저장할 때 유일 키가 막고, 다시 찾으면 먼저 만든 계정이 있어요
		when(users.findByProviderAndProviderUserId("kakao", "social-1")).thenReturn(Optional.empty(), Optional.of(winner));
		doAnswer(call -> {
			throw new DataIntegrityViolationException("uq_users_provider");
		}).when(users).saveAndFlush(any());

		AuthResponse response = service.login("kakao", request("t", null));

		assertThat(response.isNewUser()).isFalse();
		assertThat(response.user().id()).isEqualTo(winner.getId());
		assertThat(response.user().nickname()).isEqualTo("먼저 만든 사람");
	}

	@Test
	void 그_외의_저장_오류는_그대로_나가요() {
		doAnswer(call -> {
			throw new DataIntegrityViolationException("다른 제약");
		}).when(users).saveAndFlush(any());
		// 다시 찾아도 없으면(유일 키 충돌이 아니었다면) 원래 오류를 던져요
		assertThatThrownBy(() -> service.login("kakao", request("t", null))).isInstanceOf(DataIntegrityViolationException.class);
	}
}
