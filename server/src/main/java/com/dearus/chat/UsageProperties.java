package com.dearus.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml의 app.usage. 하루 대화 시간 안내에 쓰는 권장 시간(분)이에요. 스토리보드의 "하루 30분 권장"을 그대로 가정한 값이에요. */
@ConfigurationProperties(prefix = "app.usage")
public record UsageProperties(int recommendedMinutes) {

	public static final int DEFAULT_RECOMMENDED_MINUTES = 30;

	public UsageProperties {
		recommendedMinutes = recommendedMinutes > 0 ? recommendedMinutes : DEFAULT_RECOMMENDED_MINUTES;
	}
}
