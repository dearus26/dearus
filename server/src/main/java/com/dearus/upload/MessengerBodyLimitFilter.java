package com.dearus.upload;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * POST /uploads/messenger의 요청 크기가 1MB(초안)를 넘으면 본문을 읽기 전에 413을 돌려줘요.
 * Content-Length가 없는 요청은 통과시키고, 서비스가 글자 크기를 한 번 더 확인해요.
 */
@Component
public class MessengerBodyLimitFilter extends OncePerRequestFilter {

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !("POST".equals(request.getMethod()) && "/uploads/messenger".equals(request.getRequestURI()));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		if (request.getContentLengthLong() > MessengerService.MAX_TEXT_BYTES) {
			response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding(StandardCharsets.UTF_8.name());
			response.getWriter().write("{\"code\":\"PAYLOAD_TOO_LARGE\",\"message\":\"한 번에 올릴 수 있는 크기를 넘었어요. 나눠서 올려 주세요.\"}");
			return;
		}
		chain.doFilter(request, response);
	}
}
