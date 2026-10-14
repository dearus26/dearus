package com.dearus.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml의 app.jwt. secret은 환경변수 JWT_SECRET이에요. */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, int ttlDays) {
}
