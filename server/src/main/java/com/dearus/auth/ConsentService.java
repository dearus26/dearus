package com.dearus.auth;

import com.dearus.common.ApiException;
import com.dearus.common.Kst;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsentService {

	/** 이 나이 미만은 법정대리인 동의가 필요해요(개인정보 보호법 제22조의2). */
	static final int GUARDIAN_CONSENT_AGE = 14;
	private static final LocalDate EARLIEST_BIRTH_DATE = LocalDate.of(1900, 1, 1);

	private final UserRepository users;

	public ConsentService(UserRepository users) {
		this.users = users;
	}

	/**
	 * 서비스 이용 동의. 이미 유효하면 시각은 그대로 두고 알림 설정만 바뀌어요 (여러 번 불러도 같은 결과).
	 * 철회한 뒤 30일 안에 다시 동의하면 철회가 지워져서 보관 중이던 분석 데이터와 대화 기록을 이어서 써요.
	 * 생년월일은 처음 한 번만 받아요. 만 14세 미만이면 법정대리인 동의 방법을 아직 만들지 않아서 403이고, 이때는 아무것도 저장하지 않아요.
	 */
	@Transactional
	public UserResponse agree(UUID userId, ConsentRequest request) {
		User user = users.findById(userId).orElseThrow(ApiException::unauthorized);
		LocalDate birthDate = user.getBirthDate();
		if (birthDate == null) {
			birthDate = checkedBirthDate(request.birthDate(), Kst.today());
		}
		if (isUnderGuardianAge(birthDate, Kst.today())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "GUARDIAN_CONSENT_REQUIRED", "만 14세 미만은 보호자(법정대리인)의 동의가 필요해요.");
		}
		user.recordBirthDate(birthDate);
		user.agree(request.notifications());
		return UserResponse.from(user);
	}

	static LocalDate checkedBirthDate(LocalDate birthDate, LocalDate today) {
		if (birthDate == null) {
			throw ApiException.validation("birthDate", "생년월일을 입력해 주세요");
		}
		if (birthDate.isAfter(today) || birthDate.isBefore(EARLIEST_BIRTH_DATE)) {
			throw ApiException.validation("birthDate", "생년월일을 다시 확인해 주세요");
		}
		return birthDate;
	}

	/** 만 나이(생일이 지났는지까지 셈)가 14세 미만이에요. */
	static boolean isUnderGuardianAge(LocalDate birthDate, LocalDate today) {
		return Period.between(birthDate, today).getYears() < GUARDIAN_CONSENT_AGE;
	}

	/**
	 * AI 분석 동의 철회. 이후 AI를 부르는 기능은 403 AI_CONSENT_REQUIRED가 돼요.
	 * 저장된 분석 데이터(메신저 요약 · 시점 분석)와 대화 기록은 바로 지우지 않고 {@link ConsentRetention#AFTER_REVOKE} 동안 보관해요.
	 * 그 뒤의 폐기는 {@link ConsentPurgeJob}이 맡아요. 이미 AI 회사에 보낸 글자는 그 회사의 보관 기간(Anthropic은 30일 안에 삭제)만큼 남아요.
	 */
	@Transactional
	public UserResponse revokeAiConsent(UUID userId) {
		User user = users.findById(userId).orElseThrow(ApiException::unauthorized);
		user.revokeAiConsent();
		return UserResponse.from(user);
	}
}
