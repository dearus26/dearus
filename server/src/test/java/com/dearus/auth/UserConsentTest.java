package com.dearus.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

/** 동의 · 철회 규칙을 DB 없이 확인해요. */
class UserConsentTest {

	private User newUser() {
		return User.create("dev", "tester", "테스터");
	}

	@Test
	void 처음에는_동의가_필요하고_AI_동의는_유효하지_않아요() {
		User user = newUser();

		assertThat(user.needsConsent()).isTrue();
		assertThat(user.aiConsentActive()).isFalse();
		assertThat(user.isNotificationsOptIn()).isFalse();
	}

	@Test
	void 동의하면_시각이_기록되고_동의가_유효해져요() {
		User user = newUser();

		user.agree(null);

		assertThat(user.getAiConsentAt()).isNotNull();
		assertThat(user.needsConsent()).isFalse();
		assertThat(user.aiConsentActive()).isTrue();
		assertThat(user.isNotificationsOptIn()).isFalse();
	}

	@Test
	void 이미_동의했으면_다시_불러도_시각은_그대로이고_알림_설정만_바뀌어요() {
		User user = newUser();
		user.agree(false);
		OffsetDateTime first = user.getAiConsentAt();

		user.agree(true);

		assertThat(user.getAiConsentAt()).isEqualTo(first);
		assertThat(user.isNotificationsOptIn()).isTrue();
	}

	@Test
	void 알림_값을_안_보내면_기존_설정을_유지해요() {
		User user = newUser();
		user.agree(true);

		user.agree(null);

		assertThat(user.isNotificationsOptIn()).isTrue();
	}

	@Test
	void 철회하면_AI_동의만_무효가_되고_동의_화면은_다시_뜨지_않아요() {
		User user = newUser();
		user.agree(null);

		user.revokeAiConsent();

		assertThat(user.getAiRevokedAt()).isNotNull();
		assertThat(user.aiConsentActive()).isFalse();
		assertThat(user.needsConsent()).isFalse();
	}

	@Test
	void 철회를_여러_번_불러도_첫_철회_시각을_유지해요() {
		User user = newUser();
		user.agree(null);
		user.revokeAiConsent();
		OffsetDateTime revokedAt = user.getAiRevokedAt();

		user.revokeAiConsent();

		assertThat(user.getAiRevokedAt()).isEqualTo(revokedAt);
	}

	@Test
	void 동의한_적이_없으면_철회해도_아무_일도_없어요() {
		User user = newUser();

		user.revokeAiConsent();

		assertThat(user.getAiRevokedAt()).isNull();
	}

	@Test
	void 철회한_뒤_다시_동의하면_시각을_새로_기록하고_철회가_지워져요() {
		User user = newUser();
		user.agree(null);
		OffsetDateTime first = user.getAiConsentAt();
		user.revokeAiConsent();

		user.agree(null);

		assertThat(user.aiConsentActive()).isTrue();
		assertThat(user.getAiRevokedAt()).isNull();
		assertThat(user.getAiConsentAt()).isAfterOrEqualTo(first);
	}
}
