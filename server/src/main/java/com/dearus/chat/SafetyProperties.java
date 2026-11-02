package com.dearus.chat;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml의 app.safety. 위기 신호 안내에 내려주는 상담전화 목록이에요. */
@ConfigurationProperties(prefix = "app.safety")
public record SafetyProperties(List<SafetyNotice.Hotline> hotlines) {

	public SafetyProperties {
		hotlines = hotlines == null ? List.of() : List.copyOf(hotlines);
	}
}
