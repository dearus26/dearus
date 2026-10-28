package com.dearus.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dearus.common.Kst;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 동의 철회가 데이터를 바로 지우지 않고(30일 보관) 지워질 시각을 알려주는지, 다시 동의하면 그 시각이 사라지는지 확인해요. */
class ConsentServiceTest {

	private final UserRepository users = mock(UserRepository.class);
	private final ConsentService service = new ConsentService(users);
	private final UUID userId = UUID.randomUUID();

	private User consentingUser() {
		User user = User.create("dev", "tester", "테스터");
		ReflectionTestUtils.setField(user, "id", userId);
		user.agree(false);
		when(users.findById(userId)).thenReturn(Optional.of(user));
		return user;
	}

	@Test
	void 처음_동의한_사람은_지워질_시각이_없어요() {
		consentingUser();

		UserResponse response = service.agree(userId, new ConsentRequest(true, true, true, null));

		assertThat(response.aiConsentActive()).isTrue();
		assertThat(response.aiRevokedAt()).isNull();
		assertThat(response.aiDataDeleteAt()).isNull();
	}

	@Test
	void 철회하면_철회_시각으로부터_30일_뒤가_지워질_시각으로_내려가요() {
		consentingUser();

		UserResponse response = service.revokeAiConsent(userId);

		assertThat(response.aiConsentActive()).isFalse();
		assertThat(response.aiRevokedAt()).isNotNull();
		assertThat(response.aiDataDeleteAt()).isEqualTo(response.aiRevokedAt().plusDays(30));
		assertThat(response.aiDataDeleteAt().getOffset().getTotalSeconds()).isEqualTo(9 * 3600); // 한국 시간(+09:00)
	}

	@Test
	void 철회_뒤_다시_동의하면_철회와_지워질_시각이_사라져요() {
		consentingUser();
		service.revokeAiConsent(userId);

		UserResponse response = service.agree(userId, new ConsentRequest(true, true, true, null));

		assertThat(response.aiConsentActive()).isTrue();
		assertThat(response.aiRevokedAt()).isNull();
		assertThat(response.aiDataDeleteAt()).isNull();
	}

	@Test
	void 이미_철회한_사람이_다시_철회해도_지워질_시각은_처음_철회_기준이에요() {
		consentingUser();
		UserResponse first = service.revokeAiConsent(userId);

		UserResponse second = service.revokeAiConsent(userId);

		assertThat(second.aiRevokedAt()).isEqualTo(first.aiRevokedAt());
		assertThat(second.aiDataDeleteAt()).isEqualTo(first.aiDataDeleteAt());
	}

	@Test
	void 한번도_동의하지_않은_사람의_철회는_아무것도_바꾸지_않아요() {
		User user = User.create("dev", "newbie", "새내기");
		ReflectionTestUtils.setField(user, "id", userId);
		when(users.findById(userId)).thenReturn(Optional.of(user));

		UserResponse response = service.revokeAiConsent(userId);

		assertThat(response.aiRevokedAt()).isNull();
		assertThat(response.aiDataDeleteAt()).isNull();
		assertThat(Kst.now()).isNotNull();
	}
}
