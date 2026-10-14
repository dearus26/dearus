package com.dearus.common;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * 보안 설정. 서버 상태 확인(/actuator/health)과 로그인(POST /auth/*)만 열어 두고,
 * 나머지는 Authorization: Bearer JWT가 있어야 해요.
 * 로그인 실패 · 권한 없음도 API 명세의 { code, message } 형태로 내려줘요.
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		AuthenticationEntryPoint unauthorized = (request, response, e) -> writeJson(response,
			HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "로그인이 만료됐어요. 다시 로그인해 주세요.");
		AccessDeniedHandler forbidden = (request, response, e) -> writeJson(response,
			HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "이 요청은 할 수 없어요.");

		http
			.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
				.requestMatchers(HttpMethod.POST, "/auth/*").permitAll()
				.anyRequest().authenticated())
			.exceptionHandling(e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
			.oauth2ResourceServer(o -> o
				.jwt(Customizer.withDefaults())
				.authenticationEntryPoint(unauthorized)
				.accessDeniedHandler(forbidden));
		return http.build();
	}

	/** 메시지에 따옴표가 없는 고정 문구라서 JSON을 직접 만들어도 안전해요. */
	private static void writeJson(HttpServletResponse response, int status, String code, String message)
		throws java.io.IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
	}
}
