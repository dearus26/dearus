package com.dearus.persona;

import com.dearus.diary.Emotion;
import java.time.LocalDate;
import java.util.List;

/** API 명세의 Shortcut (PersonaWindow + key). */
public record ShortcutResponse(
	ShortcutKey key,
	LocalDate anchorDate,
	PersonaScope scope,
	LocalDate windowStart,
	LocalDate windowEnd,
	int diaryCount,
	int messengerWeeks,
	List<Emotion> emotions,
	RecordLevel level) {

	public static ShortcutResponse of(ShortcutKey key, PersonaWindowResponse window) {
		return new ShortcutResponse(key, window.anchorDate(), window.scope(), window.windowStart(), window.windowEnd(),
			window.diaryCount(), window.messengerWeeks(), window.emotions(), window.level());
	}
}
