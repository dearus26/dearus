package com.dearus.common;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** 주(월요일 시작) 도우미. 메신저 주간 요약과 시점 분석이 같은 주 경계를 써요. */
public final class Weeks {

	private Weeks() {
	}

	/** date가 속한 주의 월요일 */
	public static LocalDate startOf(LocalDate date) {
		return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
	}
}
