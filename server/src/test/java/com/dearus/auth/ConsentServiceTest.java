package com.dearus.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dearus.common.ApiException;
import com.dearus.common.Kst;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 생년월일 · 14세 확인과, 동의 철회가 데이터를 바로 지우지 않고(30일 보관) 지워질 시각을 알려주는지, 다시 동의하면 그 시각이 사라지는지 확인해요. */
class ConsentServiceTest {

	private final UserRepository users = mock(UserRepository.class);
	private final ConsentService service = new ConsentService(users);
	private final UUID userId = UUID.randomUUID();
	private static final LocalDate ADULT = LocalDate.of(2000, 1, 1);

	private User newUser() {
		User user = User.create("dev", "newbie", "새내기");
		ReflectionTestUtils.setField(user, "id", userId);
		when(users.findById(userId)).thenReturn(Optional.of(user));
		return user;
	}

	private User consentingUser() {
		User user = User.create("dev", "tester", "테스터");
		ReflectionTestUtils.setField(user, "id", userId);
		user.recordBirthDate(ADULT);
		user.agree(false);
		when(users.findById(userId)).thenReturn(Optional.of(user));
		return user;
	}

	@Test
	void 처음_동의한_사람은_지워질_시각이_없어요() {
		consentingUser();

		UserResponse response = service.agree(userId, new ConsentRequest(true, true, true, true, ADULT, null));

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

		UserResponse response = service.agree(userId, new ConsentRequest(true, true, true, true, ADULT, null));

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

	@Test
	void 처음_동의할_때_생년월일과_민감정보_동의를_함께_기록해요() {
		newUser();

		UserResponse response = service.agree(userId, new ConsentRequest(true, true, true, true, ADULT, true));

		assertThat(response.birthDate()).isEqualTo(ADULT);
		assertThat(response.sensitiveConsentedAt()).isNotNull();
		assertThat(response.needsConsent()).isFalse();
		assertThat(response.aiConsentActive()).isTrue();
		assertThat(response.notificationsOptIn()).isTrue();
	}

	@Test
	void 저장된_생년월일이_없는데_안_보내면_400이에요() {
		User user = newUser();

		assertThatThrownBy(() -> service.agree(userId, new ConsentRequest(true, true, true, true, null, null)))
			.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("VALIDATION_FAILED"));
		assertThat(user.getAiConsentAt()).isNull();
	}

	@Test
	void 오늘보다_뒤이거나_1900년보다_앞인_생년월일은_400이에요() {
		newUser();

		for (LocalDate wrong : new LocalDate[] {Kst.today().plusDays(1), LocalDate.of(1899, 12, 31)}) {
			assertThatThrownBy(() -> service.agree(userId, new ConsentRequest(true, true, true, true, wrong, null)))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("VALIDATION_FAILED"));
		}
	}

	@Test
	void 만_14세_미만이면_403이고_아무것도_저장하지_않아요() {
		User user = newUser();
		LocalDate thirteen = Kst.today().minusYears(14).plusDays(1); // 내일이 14번째 생일

		assertThatThrownBy(() -> service.agree(userId, new ConsentRequest(true, true, true, true, thirteen, null)))
			.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("GUARDIAN_CONSENT_REQUIRED"));
		assertThat(user.getBirthDate()).isNull();
		assertThat(user.getAiConsentAt()).isNull();
		assertThat(user.getSensitiveConsentAt()).isNull();
	}

	@Test
	void 오늘이_14번째_생일이면_동의할_수_있어요() {
		newUser();
		LocalDate fourteen = Kst.today().minusYears(14);

		UserResponse response = service.agree(userId, new ConsentRequest(true, true, true, true, fourteen, null));

		assertThat(response.birthDate()).isEqualTo(fourteen);
		assertThat(response.aiConsentActive()).isTrue();
	}

	@Test
	void 이미_생년월일이_있으면_다시_보내도_바뀌지_않고_안_보내도_돼요() {
		consentingUser();

		UserResponse ignored = service.agree(userId, new ConsentRequest(true, true, true, true, LocalDate.of(1990, 2, 2), null));
		UserResponse omitted = service.agree(userId, new ConsentRequest(true, true, true, true, null, null));

		assertThat(ignored.birthDate()).isEqualTo(ADULT);
		assertThat(omitted.birthDate()).isEqualTo(ADULT);
	}

	@Test
	void 만_나이는_생일이_지났는지까지_세요() {
		LocalDate today = LocalDate.of(2026, 11, 18);

		assertThat(ConsentService.isUnderGuardianAge(LocalDate.of(2012, 11, 18), today)).isFalse();
		assertThat(ConsentService.isUnderGuardianAge(LocalDate.of(2012, 11, 19), today)).isTrue();
		assertThat(ConsentService.isUnderGuardianAge(LocalDate.of(2012, 2, 29), LocalDate.of(2026, 2, 28))).isTrue();
	}
}
