package com.dearus.auth;

import com.dearus.common.ApiException;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private static final int NICKNAME_MAX = 50;

	private final UserRepository users;
	private final JwtService jwt;
	private final AuthProperties props;

	public AuthService(UserRepository users, JwtService jwt, AuthProperties props) {
		this.users = users;
		this.jwt = jwt;
		this.props = props;
	}

	/**
	 * 소셜 로그인. 지금은 개발용(dev)만 동작해요.
	 * kakao · google · apple은 제공자에게 토큰을 검증하는 코드를 붙일 때까지 501이에요.
	 */
	@Transactional
	public AuthResponse login(String provider, AuthRequest request) {
		String p = provider.toLowerCase(Locale.ROOT);
		return switch (p) {
			case "dev" -> {
				if (!props.devLoginEnabled()) {
					// 꺼져 있을 때는 이런 로그인 방식이 아예 없는 것처럼 보이게 해요
					throw ApiException.notFound();
				}
				yield devLogin(request);
			}
			case "kakao", "google", "apple" -> throw new ApiException(HttpStatus.NOT_IMPLEMENTED,
				"AUTH_PROVIDER_NOT_READY", "아직 준비 중인 로그인 방식이에요.");
			default -> throw ApiException.notFound();
		};
	}

	/** 개발용 가짜 로그인. token을 그대로 사용자 구분 값으로 써서, 같은 token이면 같은 사용자예요. */
	private AuthResponse devLogin(AuthRequest request) {
		String providerUserId = request.token().trim();
		String nickname = nicknameOf(request, providerUserId);

		var existing = users.findByProviderAndProviderUserId("dev", providerUserId);
		boolean isNew = existing.isEmpty();
		User user = existing.orElseGet(() -> users.save(User.create("dev", providerUserId, nickname)));

		JwtService.IssuedToken token = jwt.issue(user.getId());
		return new AuthResponse(token.value(), "Bearer", token.expiresAt(), isNew, UserResponse.from(user));
	}

	/** 닉네임이 없으면 token을 닉네임으로 쓰고, DB 길이(50자)를 넘으면 잘라요. */
	private static String nicknameOf(AuthRequest request, String fallback) {
		String nickname = request.nickname() == null || request.nickname().isBlank()
			? fallback
			: request.nickname().trim();
		return nickname.length() > NICKNAME_MAX ? nickname.substring(0, NICKNAME_MAX) : nickname;
	}
}
