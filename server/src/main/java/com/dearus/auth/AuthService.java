package com.dearus.auth;

import com.dearus.common.ApiException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 로그인(POST /auth/{provider}). 카카오 · 구글은 제공자에게 토큰을 확인한 뒤 JWT를 발급하고, 개발용(dev)은 token을 그대로 사용자 구분 값으로 써요.
 * 애플은 아직 없어서 501이에요. 설정(앱 ID · 클라이언트 ID)이 없는 제공자도 501이에요.
 * 규칙:
 * - 제공자마다 별개 계정이에요(2026.11.4 결정). 같은 사람이 카카오와 구글로 각각 로그인하면 계정이 둘이고, 이메일은 받지도 저장하지도 않아요.
 * - 닉네임은 앱이 보낸 값 → 제공자 프로필의 이름 → "사용자" 순으로 정하고 20자까지만 써요. 계정을 처음 만들 때만 정하고 다음 로그인에는 바꾸지 않아요.
 * - 제공자에게 확인하는 동안에는 DB 연결을 잡지 않아요(연결 풀이 작아요). 계정 찾기 · 만들기만 짧은 트랜잭션을 써요.
 * - 처음 로그인이 동시에 두 번 들어와도 유일 키(provider + provider_user_id)가 막아서 한 사람만 만들어져요.
 * 로그에는 토큰 없이 제공자와 결과만 남겨요.
 */
@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	static final int NICKNAME_MAX = 20;
	static final String DEFAULT_NICKNAME = "사용자";
	/** users.provider_user_id 컬럼 길이. 개발용 로그인의 token이 여기에 그대로 들어가요(소셜 ID는 이보다 짧아요). */
	static final int DEV_TOKEN_MAX = 255;
	/** API 명세가 아는 로그인 방식. 이 밖의 이름은 404예요. */
	private static final Set<String> KNOWN_SOCIAL = Set.of("kakao", "google", "apple");

	private record Found(User user, boolean isNew) {
	}

	private final UserRepository users;
	private final JwtService jwt;
	private final AuthProperties props;
	private final Map<String, SocialTokenVerifier> verifiers = new HashMap<>();
	private final TransactionTemplate tx;

	public AuthService(UserRepository users, JwtService jwt, AuthProperties props, List<SocialTokenVerifier> verifiers, TransactionTemplate tx) {
		this.users = users;
		this.jwt = jwt;
		this.props = props;
		this.tx = tx;
		verifiers.forEach(verifier -> this.verifiers.put(verifier.provider(), verifier));
	}

	public AuthResponse login(String provider, AuthRequest request) {
		String p = provider.toLowerCase(Locale.ROOT);
		if (p.equals("dev")) {
			if (!props.devLoginEnabled()) {
				// 꺼져 있을 때는 이런 로그인 방식이 아예 없는 것처럼 보이게 해요
				throw ApiException.notFound();
			}
			String providerUserId = request.token().trim();
			if (providerUserId.length() > DEV_TOKEN_MAX) {
				// 개발용 로그인은 token이 그대로 provider_user_id(255자) 컬럼에 들어가요
				throw ApiException.validation("token", DEV_TOKEN_MAX + "자까지 쓸 수 있어요");
			}
			return respond(p, findOrCreate(p, providerUserId, nicknameOf(request.nickname(), null, providerUserId)));
		}
		if (!KNOWN_SOCIAL.contains(p)) {
			throw ApiException.notFound();
		}
		SocialTokenVerifier verifier = verifiers.get(p);
		if (verifier == null || !verifier.isConfigured()) {
			throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "AUTH_PROVIDER_NOT_READY", "아직 준비 중인 로그인 방식이에요.");
		}

		VerifiedIdentity who;
		try {
			who = verifier.verify(request.token().trim()); // 제공자에게 물어요. 이 동안 DB 연결은 잡지 않아요
		} catch (AuthProviderException e) {
			throw toApiException(e);
		}
		return respond(p, findOrCreate(p, who.providerUserId(), nicknameOf(request.nickname(), who.displayName(), null)));
	}

	/** 같은 계정이면 찾고, 없으면 만들어요. 동시에 처음 로그인한 다른 요청이 먼저 만들었으면 그 계정을 써요. */
	private Found findOrCreate(String provider, String providerUserId, String nickname) {
		try {
			return tx.execute(status -> {
				Optional<User> existing = users.findByProviderAndProviderUserId(provider, providerUserId);
				if (existing.isPresent()) {
					return new Found(existing.get(), false);
				}
				return new Found(users.saveAndFlush(User.create(provider, providerUserId, nickname)), true);
			});
		} catch (DataIntegrityViolationException e) {
			User user = tx.execute(status -> users.findByProviderAndProviderUserId(provider, providerUserId).orElseThrow(() -> e));
			return new Found(user, false);
		}
	}

	private AuthResponse respond(String provider, Found found) {
		JwtService.IssuedToken token = jwt.issue(found.user().getId());
		log.info("로그인 provider={} newUser={} userId={}", provider, found.isNew(), found.user().getId());
		return new AuthResponse(token.value(), "Bearer", token.expiresAt(), found.isNew(), UserResponse.from(found.user()));
	}

	static ApiException toApiException(AuthProviderException e) {
		return switch (e.reason()) {
			case INVALID -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_PROVIDER_TOKEN_INVALID", "로그인 정보를 확인하지 못했어요. 다시 로그인해 주세요.");
			case UNAVAILABLE -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_PROVIDER_UNAVAILABLE",
				"지금은 로그인 서비스에 연결할 수 없어요. 잠시 뒤에 다시 해 주세요.");
		};
	}

	/**
	 * 앱이 보낸 이름 → 제공자 프로필의 이름 → fallback(개발용 로그인은 token) → "사용자" 순으로 첫 번째 비어 있지 않은 값을 20자까지 써요.
	 * 글자 수는 이모지가 반으로 잘리지 않게 문자(코드 포인트) 단위로 세요.
	 */
	static String nicknameOf(String requested, String providerName, String fallback) {
		for (String candidate : new String[] {requested, providerName, fallback}) {
			if (candidate != null && !candidate.isBlank()) {
				return cut(candidate.strip());
			}
		}
		return DEFAULT_NICKNAME;
	}

	private static String cut(String text) {
		return text.codePointCount(0, text.length()) <= NICKNAME_MAX ? text : text.substring(0, text.offsetByCodePoints(0, NICKNAME_MAX));
	}
}
