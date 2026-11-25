package com.dearus.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** 로그인은 IP별 · 나머지는 사람별로 세는지, 프록시 뒤에서 보낸 사람의 IP를 바르게 고르는지 확인해요. */
class RateLimitInterceptorTest {

	private final LimitProperties limits = new LimitProperties(200, 100, 5, 20, 2, 3);
	private final RateLimitInterceptor interceptor = new RateLimitInterceptor(new RateLimiter(), limits);

	@AfterEach
	void clear() {
		SecurityContextHolder.clearContext();
	}

	private static MockHttpServletRequest request(String uri, String remoteAddr, String forwardedFor) {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
		request.setRemoteAddr(remoteAddr);
		if (forwardedFor != null) {
			request.addHeader("X-Forwarded-For", forwardedFor);
		}
		return request;
	}

	private static void signIn(String userId) {
		Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "HS256"), Map.of("sub", userId));
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
	}

	private boolean pass(MockHttpServletRequest request) {
		return interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
	}

	@Test
	void 로그인은_IP마다_1분에_상한까지만_받아요() {
		assertThat(pass(request("/auth/kakao", "10.0.0.5", "203.0.113.7"))).isTrue();
		assertThat(pass(request("/auth/google", "10.0.0.5", "203.0.113.7"))).isTrue();

		assertThatThrownBy(() -> pass(request("/auth/kakao", "10.0.0.5", "203.0.113.7")))
			.isInstanceOfSatisfying(ApiException.class, e -> {
				assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
				assertThat(e.getCode()).isEqualTo("RATE_LIMITED");
				assertThat((Long) e.getDetails().get("retryAfterSeconds")).isPositive();
			});
		// 다른 IP는 따로 세요
		assertThat(pass(request("/auth/kakao", "10.0.0.5", "198.51.100.9"))).isTrue();
	}

	@Test
	void 로그인한_사람은_사람마다_세요() {
		signIn("user-a");
		for (int i = 0; i < 3; i++) {
			assertThat(pass(request("/diaries", "10.0.0.5", "203.0.113.7"))).isTrue();
		}
		assertThatThrownBy(() -> pass(request("/chats", "10.0.0.5", "203.0.113.7"))).isInstanceOf(ApiException.class);

		signIn("user-b"); // 같은 IP라도 다른 사람은 따로예요
		assertThat(pass(request("/diaries", "10.0.0.5", "203.0.113.7"))).isTrue();
	}

	@Test
	void 토큰이_없는_요청은_세지_않아요() {
		for (int i = 0; i < 10; i++) {
			assertThat(pass(request("/me", "10.0.0.5", null))).isTrue();
		}
	}

	@Test
	void 프록시_뒤에서는_오른쪽부터_처음_나오는_공인_주소를_써요() {
		// 보내는 쪽이 왼쪽에 아무 값이나 적어도, 프록시가 붙인 오른쪽 값을 믿어요
		assertThat(RateLimitInterceptor.clientIp(null, "1.1.1.1, 203.0.113.7", "10.0.0.5")).isEqualTo("203.0.113.7");
		// 프록시끼리의 사설 주소는 건너뛰어요
		assertThat(RateLimitInterceptor.clientIp(null, "203.0.113.7, 10.1.2.3, 172.16.0.9", "10.0.0.5")).isEqualTo("203.0.113.7");
		assertThat(RateLimitInterceptor.clientIp(null, "2001:db8::1, fd00::1", "10.0.0.5")).isEqualTo("2001:db8:0:0:0:0:0:1");
	}

	@Test
	void Cloudflare가_넣은_주소가_있으면_그걸_먼저_써요() {
		assertThat(RateLimitInterceptor.clientIp("198.51.100.20", "1.1.1.1, 198.51.100.20, 104.16.0.1", "10.0.0.5")).isEqualTo("198.51.100.20");
		// 주소 모양이 아니면 무시하고 다음 방법으로 가요
		assertThat(RateLimitInterceptor.clientIp("not-an-ip", "203.0.113.7", "10.0.0.5")).isEqualTo("203.0.113.7");
	}

	@Test
	void 쓸_만한_값이_없으면_연결된_주소를_써요() {
		assertThat(RateLimitInterceptor.clientIp(null, null, "127.0.0.1")).isEqualTo("127.0.0.1");
		assertThat(RateLimitInterceptor.clientIp(null, "  ", "127.0.0.1")).isEqualTo("127.0.0.1");
		assertThat(RateLimitInterceptor.clientIp(null, "unknown, evil.example.com", "127.0.0.1")).isEqualTo("127.0.0.1");
		assertThat(RateLimitInterceptor.clientIp(null, "10.0.0.1, 192.168.0.2", "127.0.0.1")).isEqualTo("127.0.0.1");
	}
}
