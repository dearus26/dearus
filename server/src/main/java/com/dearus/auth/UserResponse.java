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
			user.aiConsentActive(),
			user.needsConsent(),
			user.isNotificationsOptIn(),
			Kst.toKst(user.getCreatedAt()));
	}
}
