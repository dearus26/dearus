package com.dearus.common;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** 한국 시간(+09:00) 도우미. 응답의 시각은 항상 한국 시간으로 내려줘요. */
public final class Kst {

	public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	private Kst() {
	}

	/**
	 * 지금 시각. PostgreSQL이 마이크로초까지만 저장해서 같은 자릿수로 맞춰요.
	 * 안 맞추면 저장 직후 응답과 나중에 DB에서 읽은 응답의 시각이 달라 보여요.
	 */
	public static OffsetDateTime now() {
		return OffsetDateTime.now(ZONE).truncatedTo(ChronoUnit.MICROS);
	}

	public static LocalDate today() {
		return LocalDate.now(ZONE);
	}

	/** DB에서 어떤 시간대로 읽혀도 한국 시간으로 맞춰요. */
	public static OffsetDateTime toKst(OffsetDateTime time) {
		return time == null ? null : time.atZoneSameInstant(ZONE).toOffsetDateTime();
	}
}
