package com.dearus.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml의 app.crypto. key는 환경변수 ENCRYPTION_KEY(base64로 인코딩한 32바이트)예요. */
@ConfigurationProperties(prefix = "app.crypto")
public record CryptoProperties(String key) {
}
