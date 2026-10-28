package com.dearus.common.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml의 app.ai. apiKey는 환경변수 ANTHROPIC_API_KEY, provider는 AI_PROVIDER예요. */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(String provider, String apiKey, String model, int timeoutSeconds) {
}
