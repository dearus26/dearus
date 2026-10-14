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

	/** 서비스 이용 동의. 이미 유효하면 시각은 그대로 두고 알림 설정만 바뀌어요 (여러 번 불러도 같은 결과). */
	@Transactional
	public UserResponse agree(UUID userId, ConsentRequest request) {
		User user = users.findById(userId).orElseThrow(ApiException::unauthorized);
		user.agree(request.notifications());
		return UserResponse.from(user);
	}

	/**
	 * AI 분석 동의 철회. 이후 AI를 부르는 기능은 403 AI_CONSENT_REQUIRED가 돼요.
	 * 기획서 7장에 따라 저장된 분석 데이터(카톡 요약 · 페르소나 분석)도 즉시 지워야 해요.
	 * TODO: 해당 테이블을 만들 때 여기서 함께 삭제해요.
	 */
	@Transactional
	public UserResponse revokeAiConsent(UUID userId) {
		User user = users.findById(userId).orElseThrow(ApiException::unauthorized);
		user.revokeAiConsent();
		return UserResponse.from(user);
	}
}
