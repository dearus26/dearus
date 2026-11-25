package com.dearus.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** 1분 단위로 요청을 세는 규칙(상한까지 통과 · 넘치면 남은 초 · 다음 1분에 새로 셈 · 키마다 따로)을 확인해요. */
class RateLimiterTest {

	private final AtomicLong now = new AtomicLong(60_000L * 1000); // 어느 1분 창의 시작
	private final RateLimiter limiter = new RateLimiter(now::get);

	@Test
	void 상한까지는_통과하고_넘치면_다음_창까지_남은_초를_알려줘요() {
		for (int i = 0; i < 3; i++) {
			assertThat(limiter.acquire("ip:1.2.3.4", 3)).isZero();
		}
		now.addAndGet(20_500); // 창 안에서 20.5초 지남

		assertThat(limiter.acquire("ip:1.2.3.4", 3)).isEqualTo(40); // 39.5초 남음 → 올려서 40초
	}

	@Test
	void 다음_1분이_되면_새로_세요() {
		for (int i = 0; i < 4; i++) {
			limiter.acquire("user:a", 3);
		}
		now.addAndGet(60_000);

		assertThat(limiter.acquire("user:a", 3)).isZero();
	}

	@Test
	void 키마다_따로_세요() {
		for (int i = 0; i < 3; i++) {
			limiter.acquire("user:a", 3);
		}

		assertThat(limiter.acquire("user:a", 3)).isPositive();
		assertThat(limiter.acquire("user:b", 3)).isZero();
	}

	@Test
	void 창이_끝나기_직전에도_최소_1초를_알려줘요() {
		limiter.acquire("k", 1);
		now.addAndGet(59_999);

		assertThat(limiter.acquire("k", 1)).isEqualTo(1);
	}
}
