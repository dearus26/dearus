package com.dearus.persona;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dearus.common.ApiException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 시점의 기록 구간 · 바로가기 기준일 · 기록 수준 계산을 확인해요. 오늘은 2026-10-28(수)로 고정해요. */
class PersonaRangeTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

	@Test
	void 바로가기_구간은_기준일_전후_2주예요() {
		PersonaRange range = PersonaRange.of(PersonaScope.AROUND, LocalDate.of(2026, 9, 7), TODAY);

		assertThat(range.start()).isEqualTo(LocalDate.of(2026, 8, 24));
		assertThat(range.end()).isEqualTo(LocalDate.of(2026, 9, 21));
	}

	@Test
	void 전후_2주의_끝이_오늘을_넘으면_오늘로_줄여요() {
		PersonaRange range = PersonaRange.of(PersonaScope.AROUND, LocalDate.of(2026, 10, 19), TODAY);

		assertThat(range.start()).isEqualTo(LocalDate.of(2026, 10, 5));
		assertThat(range.end()).isEqualTo(TODAY);
	}

	@Test
	void 날짜를_직접_고르면_그날_하루예요() {
		PersonaRange range = PersonaRange.of(PersonaScope.DAY, LocalDate.of(2026, 10, 4), TODAY);

		assertThat(range.start()).isEqualTo(LocalDate.of(2026, 10, 4));
		assertThat(range.end()).isEqualTo(LocalDate.of(2026, 10, 4));
	}

	@Test
	void 오늘부터_365일_전까지만_고를_수_있어요() {
		assertThat(PersonaRange.of(PersonaScope.DAY, TODAY, TODAY).anchor()).isEqualTo(TODAY);
		assertThat(PersonaRange.of(PersonaScope.DAY, TODAY.minusDays(365), TODAY).anchor()).isEqualTo(TODAY.minusDays(365));

		for (LocalDate bad : new LocalDate[] {TODAY.plusDays(1), TODAY.minusDays(366)}) {
			assertThatThrownBy(() -> PersonaRange.of(PersonaScope.DAY, bad, TODAY))
				.isInstanceOfSatisfying(ApiException.class, e -> {
					assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
					assertThat(e.getCode()).isEqualTo("ANCHOR_OUT_OF_RANGE");
				});
		}
	}

	@Test
	void 바로가기_기준일은_그_주_월요일이에요() {
		assertThat(PersonaRange.shortcutAnchor(ShortcutKey.ONE_MONTH, TODAY)).isEqualTo(LocalDate.of(2026, 9, 28));
		assertThat(PersonaRange.shortcutAnchor(ShortcutKey.SIX_MONTHS, TODAY)).isEqualTo(LocalDate.of(2026, 4, 27));
		for (ShortcutKey key : ShortcutKey.values()) {
			assertThat(PersonaRange.shortcutAnchor(key, TODAY).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
		}
	}

	@Test
	void 일년_전_바로가기의_월요일이_365일을_넘으면_다음_월요일로_잡아요() {
		// 2025-10-28(화) -> 그 주 월요일 2025-10-27은 오늘로부터 366일 전이라 범위 밖이에요
		LocalDate anchor = PersonaRange.shortcutAnchor(ShortcutKey.ONE_YEAR, TODAY);

		assertThat(anchor).isEqualTo(LocalDate.of(2025, 11, 3));
		assertThat(anchor.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
		// 그리고 실제로 고를 수 있는 날짜예요
		assertThat(PersonaRange.of(PersonaScope.AROUND, anchor, TODAY).anchor()).isEqualTo(anchor);
	}

	@Test
	void 모든_날에서_바로가기_기준일은_고를_수_있는_범위_안이에요() {
		for (int d = 0; d < 400; d++) {
			LocalDate today = LocalDate.of(2026, 1, 1).plusDays(d);
			for (ShortcutKey key : ShortcutKey.values()) {
				LocalDate anchor = PersonaRange.shortcutAnchor(key, today);
				assertThat(anchor).as("%s %s", today, key).isBetween(today.minusDays(365), today);
			}
		}
	}

	@Test
	void 메신저_주_범위는_전후_2주는_겹치는_주_하루는_그날이_속한_주예요() {
		PersonaRange around = PersonaRange.of(PersonaScope.AROUND, LocalDate.of(2026, 9, 7), TODAY);
		// 구간 8/24(월) ~ 9/21(월): 8/24이 월요일이라 그 전 주(8/17)는 겹치지 않고, 9/21 주(월요일 9/21)까지 겹쳐요
		assertThat(around.weekFrom()).isEqualTo(LocalDate.of(2026, 8, 18)); // start - 6일, 이 날짜 이후 월요일부터 포함
		assertThat(around.weekTo()).isEqualTo(LocalDate.of(2026, 9, 21));

		PersonaRange day = PersonaRange.of(PersonaScope.DAY, LocalDate.of(2026, 10, 7), TODAY); // 수요일
		assertThat(day.weekFrom()).isEqualTo(LocalDate.of(2026, 10, 5));
		assertThat(day.weekTo()).isEqualTo(LocalDate.of(2026, 10, 5));
	}

	@Test
	void 기록_수준은_명세의_초안_기준이에요() {
		// 전후 2주
		assertThat(RecordLevel.of(PersonaScope.AROUND, 0, 0)).isEqualTo(RecordLevel.NONE);
		assertThat(RecordLevel.of(PersonaScope.AROUND, 1, 0)).isEqualTo(RecordLevel.LOW);
		assertThat(RecordLevel.of(PersonaScope.AROUND, 4, 0)).isEqualTo(RecordLevel.LOW);
		assertThat(RecordLevel.of(PersonaScope.AROUND, 5, 0)).isEqualTo(RecordLevel.ENOUGH);
		assertThat(RecordLevel.of(PersonaScope.AROUND, 2, 1)).isEqualTo(RecordLevel.ENOUGH);
		assertThat(RecordLevel.of(PersonaScope.AROUND, 0, 1)).isEqualTo(RecordLevel.ENOUGH);
		assertThat(RecordLevel.of(PersonaScope.AROUND, 9, 0)).isEqualTo(RecordLevel.ENOUGH);
		assertThat(RecordLevel.of(PersonaScope.AROUND, 10, 0)).isEqualTo(RecordLevel.RICH);
		assertThat(RecordLevel.of(PersonaScope.AROUND, 12, 3)).isEqualTo(RecordLevel.RICH);
	}

	@Test
	void 하루_단위의_기록_수준은_그날의_일기만_봐요() {
		assertThat(RecordLevel.of(PersonaScope.DAY, 1, 0)).isEqualTo(RecordLevel.ENOUGH);
		assertThat(RecordLevel.of(PersonaScope.DAY, 1, 1)).isEqualTo(RecordLevel.ENOUGH);
		// 메신저 말투 분석이 있어도 그날의 일기가 없으면 NONE이에요
		assertThat(RecordLevel.of(PersonaScope.DAY, 0, 1)).isEqualTo(RecordLevel.NONE);
	}
}
