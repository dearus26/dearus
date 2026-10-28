package com.dearus.auth;

import com.dearus.common.ApiException;
import com.dearus.persona.PersonaAnalysisRepository;
import com.dearus.upload.MessengerUploadRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsentService {

	private final UserRepository users;
	private final MessengerUploadRepository messengerUploads;
	private final PersonaAnalysisRepository personaAnalyses;

	public ConsentService(UserRepository users, MessengerUploadRepository messengerUploads,
		PersonaAnalysisRepository personaAnalyses) {
		this.users = users;
		this.messengerUploads = messengerUploads;
		this.personaAnalyses = personaAnalyses;
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
	 * 기획서 7장에 따라 저장된 분석 데이터(메신저 요약 · 시점 분석)도 즉시 지워요.
	 * 이미 AI 회사에 보낸 글자는 그 회사의 보관 기간(Anthropic은 30일 안에 삭제)만큼 남아요.
	 */
	@Transactional
	public UserResponse revokeAiConsent(UUID userId) {
		User user = users.findById(userId).orElseThrow(ApiException::unauthorized);
		user.revokeAiConsent();
		messengerUploads.deleteAllByUserId(userId);
		personaAnalyses.deleteAllByUserId(userId);
		return UserResponse.from(user);
	}
}
