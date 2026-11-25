package com.dearus.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml의 app.admin. password는 환경변수 ADMIN_PASSWORD예요(2026.11.25).
 * 비워 두면 관리자 로그인이 꺼져요(404 ADMIN_DISABLED). 채우면 16자 이상이어야 서버가 켜져요.
 * tokenHours는 관리자 토큰이 유지되는 시간이에요.
 */
@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String password, int tokenHours) {

	static final int MIN_PASSWORD_LENGTH = 16;

	public AdminProperties {
		if (password != null && !password.isEmpty() && password.length() < MIN_PASSWORD_LENGTH) {
			throw new IllegalStateException("ADMIN_PASSWORD는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 해요. 비워 두면 관리자 페이지가 꺼져요.");
		}
		if (tokenHours <= 0) {
			tokenHours = 8;
		}
	}

	public boolean enabled() {
		return password != null && !password.isEmpty();
	}
}
