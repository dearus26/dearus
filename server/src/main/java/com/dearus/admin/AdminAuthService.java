package com.dearus.admin;

import com.dearus.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 관리자 로그인이에요. 비밀번호(ADMIN_PASSWORD) 하나를 팀원이 함께 써요(2026.11.25 결정).
 * 같은 길이의 해시로 바꿔서 비교해서, 비밀번호가 얼마나 맞았는지 응답 시간으로 알 수 없어요.
 * 마구 넣어 보기는 로그인 횟수 제한(IP당 1분 10번, RateLimitInterceptor)이 막아요. 입력한 비밀번호는 로그에 남기지 않아요.
 */
@Service
public class AdminAuthService {

	private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

	private final AdminProperties props;
	private final AdminTokens tokens;

	public AdminAuthService(AdminProperties props, AdminTokens tokens) {
		this.props = props;
		this.tokens = tokens;
	}

	public AdminTokens.IssuedToken login(String password) {
		if (!props.enabled()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "ADMIN_DISABLED", "관리자 페이지가 꺼져 있어요. 서버에 ADMIN_PASSWORD를 넣어야 켜져요.");
		}
		if (password == null || !MessageDigest.isEqual(sha256(password), sha256(props.password()))) {
			log.warn("관리자 로그인 실패");
			throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_LOGIN_FAILED", "비밀번호가 맞지 않아요.");
		}
		log.info("관리자 로그인");
		return tokens.issue();
	}

	private static byte[] sha256(String text) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
