package com.dearus.admin;

import com.dearus.common.SecurityConfig;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * 관리자 페이지(/admin)와 관리자 API(/admin/api/**)의 보안 설정이에요(2026.11.25). 앱의 보안 설정보다 먼저 봐요.
 * 페이지 파일(HTML · JS · CSS)과 관리자 로그인만 열어 두고, 나머지 관리자 API는 관리자 토큰(AdminTokens)이 있어야 해요.
 * 앱 사용자 토큰으로는 관리자 API를 못 쓰고, 관리자 토큰으로는 앱 API를 못 써요(서명 키 · 발급자가 달라요).
 */
@Configuration
@EnableConfigurationProperties(AdminProperties.class)
public class AdminSecurityConfig {

	/** 페이지는 우리 서버의 파일만 불러오고, 다른 사이트 안에 끼워 넣을 수 없어요. */
	static final String CONTENT_SECURITY_POLICY =
		"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; "
			+ "frame-ancestors 'none'; base-uri 'none'; form-action 'none'";

	@Bean
	@Order(1)
	SecurityFilterChain adminSecurityFilterChain(HttpSecurity http, AdminTokens tokens) throws Exception {
		AuthenticationEntryPoint unauthorized = (request, response, e) -> SecurityConfig.writeJson(response,
			HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "관리자 로그인이 필요해요.");
		AccessDeniedHandler forbidden = (request, response, e) -> SecurityConfig.writeJson(response,
			HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "이 요청은 할 수 없어요.");

		http
			.securityMatcher("/admin/**")
			.csrf(AbstractHttpConfigurer::disable) // 쿠키 없이 Authorization 헤더로만 확인해요
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.GET, "/admin", "/admin/", "/admin/index.html", "/admin/admin.js", "/admin/admin.css")
				.permitAll()
				.requestMatchers(HttpMethod.POST, "/admin/api/login").permitAll()
				.requestMatchers("/admin/api/**").authenticated()
				.anyRequest().denyAll())
			.headers(h -> h
				.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
				.referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER)))
			.exceptionHandling(e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
			.oauth2ResourceServer(o -> o
				.jwt(j -> j.decoder(tokens.decoder()))
				.authenticationEntryPoint(unauthorized)
				.accessDeniedHandler(forbidden));
		return http.build();
	}
}
