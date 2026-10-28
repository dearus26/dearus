package com.dearus.auth;

import com.dearus.common.Kst;
import java.time.OffsetDateTime;
import java.util.UUID;

/** API 명세의 User 스키마. */
public record UserResponse(
	UUID id,
	String provider,
	String nickname,
	OffsetDateTime aiConsentedAt,
	OffsetDateTime aiRevokedAt,
	OffsetDateTime aiDataDeleteAt,
	boolean aiConsentActive,
	boolean needsConsent,
	boolean notificationsOptIn,
	OffsetDateTime createdAt) {

	public static UserResponse from(User user) {
		return new UserResponse(
			user.getId(),
			user.getProvider(),
			user.getNickname(),
			Kst.toKst(user.getAiConsentAt()),
			Kst.toKst(user.getAiRevokedAt()),
			aiDataDeleteAt(user),
			user.aiConsentActive(),
			user.needsConsent(),
			user.isNotificationsOptIn(),
			Kst.toKst(user.getCreatedAt()));
	}

	/** 철회한 상태일 때만, 보관 중인 분석 데이터와 대화 기록이 지워지는 시각(철회 시각 + 30일)이에요. */
	static OffsetDateTime aiDataDeleteAt(User user) {
		if (user.aiConsentActive() || user.getAiRevokedAt() == null) {
			return null;
		}
		return Kst.toKst(user.getAiRevokedAt().plus(ConsentRetention.AFTER_REVOKE));
	}
}
