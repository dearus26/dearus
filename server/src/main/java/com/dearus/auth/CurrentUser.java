package com.dearus.auth;

import com.dearus.common.ApiException;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** 요청의 JWT에서 로그인한 사용자 ID를 꺼내요. */
public final class CurrentUser {

	private CurrentUser() {
	}

	public static UUID id(Jwt jwt) {
		try {
			return UUID.fromString(jwt.getSubject());
		} catch (IllegalArgumentException | NullPointerException e) {
			throw ApiException.unauthorized();
		}
	}
}
