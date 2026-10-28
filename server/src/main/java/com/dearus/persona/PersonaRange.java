package com.dearus.persona;

import com.dearus.common.ApiException;
import com.dearus.common.Weeks;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;

/**
 * 시점 하나가 가리키는 기록 구간이에요. 오늘 이후의 기록은 쓰지 않아서 end는 오늘을 넘지 않아요.
 * 기준일(anchor)은 오늘부터 365일 전까지만 고를 수 있어요.
 */
public record PersonaRange(PersonaScope scope, LocalDate anchor, LocalDate start, LocalDate end) {

	static final int MAX_DAYS_BACK = 365;
	static final int AROUND_DAYS = 14;

	/** 고를 수 있는 날짜인지 확인하고(아니면 400 ANCHOR_OUT_OF_RANGE) 구간을 만들어요. */
	public static PersonaRange of(PersonaScope scope, LocalDate anchor, LocalDate today) {
		if (anchor.isAfter(today) || anchor.isBefore(today.minusDays(MAX_DAYS_BACK))) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "ANCHOR_OUT_OF_RANGE", "고를 수 없는 날짜예요. 오늘부터 1년 전까지 고를 수 있어요.");
		}
		return at(scope, anchor, today);
	}

	/**
	 * 날짜 범위를 확인하지 않고 구간만 계산해요. 이미 만든 세션의 구간을 다시 계산할 때 써요(asOf는 세션을 만든 날짜).
	 * 구간의 끝은 asOf를 넘지 않아요.
	 */
	public static PersonaRange at(PersonaScope scope, LocalDate anchor, LocalDate today) {
		return switch (scope) {
			case DAY -> new PersonaRange(scope, anchor, anchor, anchor);
			case AROUND -> {
				LocalDate end = anchor.plusDays(AROUND_DAYS);
				yield new PersonaRange(scope, anchor, anchor.minusDays(AROUND_DAYS), end.isAfter(today) ? today : end);
			}
		};
	}

	/**
	 * 바로가기의 기준일. 오늘에서 N개월 전의 그 주 월요일이고(같은 주에는 같은 분석을 재사용하려고),
	 * 그 월요일이 365일보다 더 옛날이면(1년 전 바로가기에서 생겨요) 다음 월요일로 잡아서 날짜 범위 안에 들어오게 해요.
	 */
	public static LocalDate shortcutAnchor(ShortcutKey key, LocalDate today) {
		LocalDate monday = Weeks.startOf(today.minusMonths(key.months()));
		return monday.isBefore(today.minusDays(MAX_DAYS_BACK)) ? monday.plusWeeks(1) : monday;
	}

	/**
	 * 이 구간에 쓰는 메신저 주간 요약의 weekStart 범위(양 끝 포함).
	 * AROUND는 구간과 하루라도 겹치는 주(weekStart가 start-6 ~ end), DAY는 그날이 속한 주 하나예요.
	 */
	public LocalDate weekFrom() {
		return scope == PersonaScope.DAY ? Weeks.startOf(anchor) : start.minusDays(6);
	}

	public LocalDate weekTo() {
		return scope == PersonaScope.DAY ? Weeks.startOf(anchor) : end;
	}
}
