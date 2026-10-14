package com.dearus.auth;

import java.time.OffsetDateTime;

/** API 명세의 AuthResponse. */
public record AuthResponse(
	String accessToken,
	String tokenType,
	OffsetDateTime expiresAt,
	boolean isNewUser,
	UserResponse user) {
}
