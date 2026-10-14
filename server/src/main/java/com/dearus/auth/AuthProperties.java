package com.dearus.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml의 app.auth. devLoginEnabled는 환경변수 DEV_LOGIN_ENABLED예요 (기본 꺼짐). */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(boolean devLoginEnabled) {
}
