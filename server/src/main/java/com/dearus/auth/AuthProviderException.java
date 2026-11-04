package com.dearus.auth;

/** 소셜 토큰을 확인하다 실패했어요. 토큰 문제(401)와 제공자 장애(503)를 나눠서 앱이 다르게 안내하게 해요. */
public class AuthProviderException extends RuntimeException {

	public enum Reason {
		/** 토큰이 잘못됐거나 만료됐거나 다른 앱(클라이언트)에서 발급된 토큰이에요 → 401 AUTH_PROVIDER_TOKEN_INVALID */
		INVALID,
		/** 제공자 서버나 공개키에 닿지 못했어요(네트워크 · 5xx · 제한) → 503 AUTH_PROVIDER_UNAVAILABLE */
		UNAVAILABLE
	}

	private final Reason reason;

	public AuthProviderException(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public AuthProviderException(Reason reason, String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}
}
