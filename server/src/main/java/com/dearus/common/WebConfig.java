package com.dearus.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 요청 횟수 제한(RateLimitInterceptor)을 모든 API에 걸어요. 서버 상태 확인(/actuator)은 빼요. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final LimitProperties limits;
	private final RateLimiter limiter = new RateLimiter();

	public WebConfig(LimitProperties limits) {
		this.limits = limits;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new RateLimitInterceptor(limiter, limits)).excludePathPatterns("/actuator/**");
	}
}
