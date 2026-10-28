package com.dearus.persona;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dearus.common.Kst;
import com.dearus.diary.Emotion;
import com.dearus.persona.PersonaInput.DiaryEntry;
import com.dearus.persona.PersonaInput.WeekEntry;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 시점 고르기 화면용 응답(바로가기 3개 · 고른 날짜의 기록량)을 만드는 규칙을 확인해요. */
class PersonaWindowServiceTest {

	private final PersonaCollector collector = mock(PersonaCollector.class);
	private final PersonaWindowService service = new PersonaWindowService(collector);
	private final UUID userId = UUID.randomUUID();

	private DiaryEntry diary(LocalDate date, Emotion... emotions) {
		return new DiaryEntry(UUID.randomUUID(), date, Kst.now(), List.of(emotions), "일기");
	}

	private WeekEntry week(LocalDate weekStart) {
		return new WeekEntry(UUID.randomUUID(), weekStart, Kst.now(), new byte[1], new byte[1], new byte[1]);
	}

	@Test
	void 날짜를_고르면_그날의_일기_건수_감정_수준을_알려줘요() {
		LocalDate day = Kst.today().minusDays(24);
		when(collector.collect(eq(userId), any())).thenAnswer(call -> new PersonaInput(call.getArgument(1),
			List.of(diary(day, Emotion.ANGRY, Emotion.TIRED)), List.of()));

		PersonaWindowResponse response = service.window(userId, PersonaScope.DAY, day);

		assertThat(response.anchorDate()).isEqualTo(day);
		assertThat(response.scope()).isEqualTo(PersonaScope.DAY);
		assertThat(response.windowStart()).isEqualTo(day);
		assertThat(response.windowEnd()).isEqualTo(day);
		assertThat(response.diaryCount()).isEqualTo(1);
		assertThat(response.messengerWeeks()).isZero();
		assertThat(response.emotions()).containsExactly(Emotion.TIRED, Emotion.ANGRY); // 선언 순서
		assertThat(response.level()).isEqualTo(RecordLevel.ENOUGH);
	}

	@Test
	void 하루_단위에_메신저_말투_분석이_있으면_messengerWeeks가_1이고_수준은_그대로예요() {
		LocalDate day = Kst.today().minusDays(24);
		when(collector.collect(eq(userId), any())).thenAnswer(call -> new PersonaInput(call.getArgument(1),
			List.of(), List.of(week(LocalDate.of(2026, 10, 5)))));

		PersonaWindowResponse response = service.window(userId, PersonaScope.DAY, day);

		assertThat(response.messengerWeeks()).isEqualTo(1);
		assertThat(response.level()).isEqualTo(RecordLevel.NONE);
	}

	@Test
	void 전후_2주를_고르면_감정은_비어_있어요() {
		LocalDate anchor = Kst.today().minusDays(60);
		when(collector.collect(eq(userId), any())).thenAnswer(call -> new PersonaInput(call.getArgument(1),
			List.of(diary(anchor, Emotion.JOY), diary(anchor.plusDays(1), Emotion.SAD)), List.of(week(anchor))));

		PersonaWindowResponse response = service.window(userId, PersonaScope.AROUND, anchor);

		assertThat(response.emotions()).isEmpty();
		assertThat(response.diaryCount()).isEqualTo(2);
		assertThat(response.messengerWeeks()).isEqualTo(1);
		assertThat(response.level()).isEqualTo(RecordLevel.ENOUGH); // 메신저 1주 이상
	}

	@Test
	void 바로가기는_1개월_6개월_1년_순서로_세_개이고_각각_전후_2주예요() {
		when(collector.collect(eq(userId), any())).thenAnswer(call -> new PersonaInput(call.getArgument(1), List.of(), List.of()));

		ShortcutListResponse response = service.shortcuts(userId);

		assertThat(response.items()).extracting(ShortcutResponse::key)
			.containsExactly(ShortcutKey.ONE_MONTH, ShortcutKey.SIX_MONTHS, ShortcutKey.ONE_YEAR);
		assertThat(response.items()).allSatisfy(item -> {
			assertThat(item.scope()).isEqualTo(PersonaScope.AROUND);
			assertThat(item.windowStart()).isEqualTo(item.anchorDate().minusDays(14));
			assertThat(item.level()).isEqualTo(RecordLevel.NONE);
		});
		LocalDate today = Kst.today();
		assertThat(response.items().get(0).anchorDate()).isEqualTo(PersonaRange.shortcutAnchor(ShortcutKey.ONE_MONTH, today));
	}
}
