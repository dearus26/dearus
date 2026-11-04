package com.dearus.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 대화 시간 규칙(메시지 간격 합, 한 간격은 최대 5분)을 확인해요. */
class ChatDurationTest {

	private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-11-04T10:00:00+09:00");

	@Test
	void 연속한_메시지_사이_간격을_더해요() {
		List<OffsetDateTime> times = List.of(T0, T0.plusMinutes(1), T0.plusMinutes(3), T0.plusMinutes(4));

		assertThat(ChatDuration.total(times)).isEqualTo(Duration.ofMinutes(4));
	}

	@Test
	void 한_간격은_5분까지만_인정해요() {
		// 자리를 비운 2시간과 하루가 통째로 더해지지 않아요
		List<OffsetDateTime> times = List.of(T0, T0.plusMinutes(2), T0.plusHours(2), T0.plusHours(2).plusMinutes(1), T0.plusDays(1));

		assertThat(ChatDuration.total(times)).isEqualTo(Duration.ofMinutes(2 + 5 + 1 + 5));
	}

	@Test
	void 메시지가_없거나_하나뿐이면_0이에요() {
		assertThat(ChatDuration.total(List.of())).isEqualTo(Duration.ZERO);
		assertThat(ChatDuration.total(List.of(T0))).isEqualTo(Duration.ZERO);
	}

	@Test
	void 시각이_거꾸로_간_간격은_무시해요() {
		assertThat(ChatDuration.total(List.of(T0.plusMinutes(5), T0))).isEqualTo(Duration.ZERO);
	}

	@Test
	void 분으로_반올림하고_조금이라도_대화했으면_1분이에요() {
		assertThat(ChatDuration.minutes(Duration.ZERO)).isZero();
		assertThat(ChatDuration.minutes(Duration.ofMillis(300))).isEqualTo(1);
		assertThat(ChatDuration.minutes(Duration.ofSeconds(29))).isEqualTo(1);
		assertThat(ChatDuration.minutes(Duration.ofSeconds(89))).isEqualTo(1);
		assertThat(ChatDuration.minutes(Duration.ofSeconds(90))).isEqualTo(2);
		assertThat(ChatDuration.minutes(Duration.ofMinutes(18))).isEqualTo(18);
	}
}
