package com.dearus.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 진짜 DB(server/.env의 Supabase)에서, 같은 계정이 거의 동시에 처음 로그인해도 유일 키(provider + provider_user_id)가 막아서
 * 한 명만 만들어지고 모든 요청이 같은 사용자를 돌려받는지 확인하는 시험이에요. 소셜 제공자 없이 개발용(dev) 로그인으로 해요.
 * DB를 건드려서 DEARUS_DB_TEST=true일 때만 돌고, 만든 사용자는 끝나면 지워요.
 * 돌리기: DEARUS_DB_TEST=true ./gradlew test --tests '*AuthDbTest' -i
 */
@EnabledIfEnvironmentVariable(named = "DEARUS_DB_TEST", matches = "true")
@SpringBootTest(properties = {"app.ai.provider=fake", "app.auth.dev-login-enabled=true"})
class AuthDbTest {

	@Autowired AuthService auth;
	@Autowired UserRepository users;

	@Test
	void 같은_계정이_동시에_처음_로그인해도_한_명만_만들어져요() throws Exception {
		String token = "dbtest-" + UUID.randomUUID();
		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch go = new CountDownLatch(1);
		try {
			List<Future<AuthResponse>> futures = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				futures.add(pool.submit(() -> {
					ready.countDown();
					go.await();
					return auth.login("dev", new AuthRequest(token, "동시"));
				}));
			}
			ready.await();
			go.countDown(); // 모두 한꺼번에 출발해요

			Set<UUID> userIds = new java.util.HashSet<>();
			long created = 0;
			for (Future<AuthResponse> future : futures) {
				AuthResponse response = future.get(60, TimeUnit.SECONDS);
				userIds.add(response.user().id());
				if (response.isNewUser()) {
					created++;
				}
			}

			assertThat(userIds).hasSize(1); // 모두 같은 사용자를 받았어요
			assertThat(created).isEqualTo(1); // 계정을 만든 요청은 정확히 하나예요
			assertThat(users.findByProviderAndProviderUserId("dev", token)).isPresent();

			// 그 뒤 로그인은 새 계정이 아니에요
			assertThat(auth.login("dev", new AuthRequest(token, null)).isNewUser()).isFalse();
		} finally {
			pool.shutdownNow();
			users.findByProviderAndProviderUserId("dev", token).ifPresent(users::delete);
		}
	}
}
