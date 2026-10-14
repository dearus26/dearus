package com.dearus.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class KstTest {

	@Test
	void 지금_시각은_한국_시간이고_마이크로초까지만_가져요() {
		OffsetDateTime now = Kst.now();

		assertThat(now.getOffset()).isEqualTo(ZoneOffset.ofHours(9));
		// DB(PostgreSQL)가 마이크로초까지만 저장하므로, 나노초 자릿수가 남아 있으면 안 돼요
		assertThat(now.getNano() % 1_000).isZero();
	}

	@Test
	void 어떤_시간대로_읽어도_한국_시간으로_바꿔요() {
		OffsetDateTime utc = OffsetDateTime.of(2026, 10, 14, 0, 0, 0, 0, ZoneOffset.UTC);

		OffsetDateTime kst = Kst.toKst(utc);

		assertThat(kst.getOffset()).isEqualTo(ZoneOffset.ofHours(9));
		assertThat(kst.toInstant()).isEqualTo(utc.toInstant());
		assertThat(kst.getHour()).isEqualTo(9);
	}

	@Test
	void null은_null로_돌려줘요() {
		assertThat(Kst.toKst(null)).isNull();
	}
}
