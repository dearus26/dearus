package com.dearus.auth;

import com.dearus.common.ApiException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsentService {

	private final UserRepository users;

	public ConsentService(UserRepository users) {
		this.users = users;
	}

	/**
	 * 서비스 이용 동의. 이미 유효하면 시각은 그대로 두고 알림 설정만 바뀌어요 (여러 번 불러도 같은 결과).
	 * 철회한 뒤 30일 안에 다시 동의하면 철회가 지워져서 보관 중이던 분석 데이터와 대화 기록을 이어서 써요.
	 */
	@Transactional
	public UserResponse agree(UUID userId, ConsentRequest request) {
		User user = users.findById(userId).orElseThrow(ApiException::unauthorized);
		user.agree(request.notifications());
		return UserResponse.from(user);
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
