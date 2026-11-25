package com.dearus.common;

import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 하루 AI 사용 상한(app.limits)을 확인할 때 같이 쓰는 것들이에요. 하루는 한국 시간 자정부터예요.
 * 새 테이블 없이 DB에 이미 있는 기록(보낸 내 말 · 올린 업로드 · 만든 리포트)을 세요. 그래서 서버가 다시 켜져도 정확해요.
 */
public final class DailyLimit {

	private DailyLimit() {
	}

	/** 오늘(한국 시간) 0시예요. */
	public static OffsetDateTime startOfToday() {
		return Kst.today().atStartOfDay(Kst.ZONE).toOffsetDateTime();
	}

	/** 오늘 쓴 양이 상한에 닿았으면 429 AI_DAILY_LIMIT을 던져요. */
	public static void check(long usedToday, int limit, String message) {
		if (usedToday >= limit) {
			OffsetDateTime resetAt = Kst.today().plusDays(1).atStartOfDay(Kst.ZONE).toOffsetDateTime();
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AI_DAILY_LIMIT", message,
				Map.of("limit", limit, "resetAt", resetAt.toString()));
		}
	}
}
