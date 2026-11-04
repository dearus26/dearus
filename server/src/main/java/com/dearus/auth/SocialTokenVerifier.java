package com.dearus.auth;

/**
 * 소셜 SDK가 준 토큰을 제공자 기준으로 확인하는 곳이에요. 제공자 하나에 구현 하나라서, 애플을 더할 때도 구현만 더하면 돼요.
 * 토큰 값은 로그나 오류 메시지에 남기지 않아요.
 */
public interface SocialTokenVerifier {

	/** POST /auth/{provider}의 provider 값(소문자) */
	String provider();

	/** 필요한 설정(앱 ID · 클라이언트 ID)이 들어 있는지. 아니면 이 로그인 방식은 501이에요. */
	boolean isConfigured();

	/** 토큰이 올바르면 그 사람을 돌려주고, 아니면 {@link AuthProviderException}을 던져요. */
	VerifiedIdentity verify(String token);
}
