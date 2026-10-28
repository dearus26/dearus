package com.dearus.persona;

import com.dearus.diary.Emotion;
import java.time.LocalDate;
import java.util.List;

/** API 명세의 PersonaWindow. emotions는 DAY일 때만 채워지고 AROUND는 빈 목록이에요. */
public record PersonaWindowResponse(
	LocalDate anchorDate,
	PersonaScope scope,
	LocalDate windowStart,
	LocalDate windowEnd,
	int diaryCount,
	int messengerWeeks,
	List<Emotion> emotions,
	RecordLevel level) {

	public static PersonaWindowResponse from(PersonaInput input) {
		PersonaRange range = input.range();
		List<Emotion> emotions = range.scope() == PersonaScope.DAY
			? input.diaries().stream().flatMap(d -> d.emotions().stream()).distinct().sorted().toList()
			: List.of();
		return new PersonaWindowResponse(range.anchor(), range.scope(), range.start(), range.end(),
			input.diaries().size(), input.weeks().size(), emotions, input.level());
	}
}
