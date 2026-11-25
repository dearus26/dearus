package com.dearus.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 짧은 시간에 몰리는 요청을 막아요(2026.11.25). 로그인(POST /auth/*)은 IP별로, 나머지는 로그인한 사람별로 1분 단위로 세요.
 * 넘치면 429 RATE_LIMITED이고 Retry-After 헤더로 기다릴 초를 알려줘요.
 * Spring Security 뒤에서 돌아서, 로그인이 필요한 API는 토큰 확인이 끝난 뒤에 세요(토큰이 없으면 여기까지 오지 않고 401이에요).
 */
public class RateLimitInterceptor implements HandlerInterceptor {

	private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
	private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:.]+$");

	private final RateLimiter limiter;
	private final LimitProperties limits;

	public RateLimitInterceptor(RateLimiter limiter, LimitProperties limits) {
		this.limiter = limiter;
		this.limits = limits;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String key;
		int limit;
		if (request.getRequestURI().startsWith("/auth/")) {
			key = "ip:" + clientIp(request.getHeader("CF-Connecting-IP"), request.getHeader("X-Forwarded-For"), request.getRemoteAddr());
			limit = limits.loginPerMinute();
		} else {
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			if (!(auth instanceof JwtAuthenticationToken jwt)) {
				return true;
			}
			key = "user:" + jwt.getName();
			limit = limits.requestsPerMinute();
		}
		long retryAfter = limiter.acquire(key, limit);
		if (retryAfter > 0) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "요청이 너무 많아요. 잠시 뒤에 다시 해 주세요.",
				Map.of("retryAfterSeconds", retryAfter));
		}
		return true;
	}

	/**
	 * 요청을 보낸 사람의 IP예요. 주소 모양의 글자만 다뤄서 DNS 조회는 하지 않아요.
	 * 1. Render는 Cloudflare를 거쳐 들어와서, Cloudflare가 넣는 CF-Connecting-IP(보낸 사람의 주소)를 먼저 써요.
	 * 2. 없으면 X-Forwarded-For를 오른쪽부터 보며 사설 · 내부 주소(프록시끼리의 주소)는 건너뛰고 처음 나오는 공인 주소를 써요.
	 *    왼쪽 값은 보내는 쪽이 마음대로 적을 수 있어서예요.
	 * 3. 그것도 없으면 연결된 주소(remoteAddr)예요.
	 * 프록시 없이 서버에 바로 붙을 수 있는 곳에 띄우면 두 헤더를 꾸며 보낼 수 있어서, 로그인 횟수 제한을 피할 수 있어요.
	 */
	static String clientIp(String cfConnectingIp, String forwardedFor, String remoteAddr) {
		InetAddress cloudflare = cfConnectingIp == null ? null : literal(cfConnectingIp.strip());
		if (cloudflare != null) {
			return cloudflare.getHostAddress();
		}
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			String[] hops = forwardedFor.split(",");
			for (int i = hops.length - 1; i >= 0; i--) {
				String hop = hops[i].strip();
				InetAddress address = literal(hop);
				if (address != null && !isInternal(address)) {
					return address.getHostAddress();
				}
			}
		}
		return remoteAddr;
	}

	private static InetAddress literal(String value) {
		if (!IPV4.matcher(value).matches() && !(value.contains(":") && IPV6.matcher(value).matches())) {
			return null;
		}
		try {
			return InetAddress.getByName(value); // 주소 모양일 때만 불러서 이름 조회가 일어나지 않아요
		} catch (UnknownHostException | SecurityException e) {
			return null;
		}
	}

	private static boolean isInternal(InetAddress address) {
		byte[] raw = address.getAddress();
		boolean uniqueLocalV6 = raw.length == 16 && (raw[0] & 0xFE) == 0xFC;
		return address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
			|| address.isAnyLocalAddress() || uniqueLocalV6;
	}
}
