package com.dearus.persona;

import com.dearus.diary.Emotion;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 한 시점을 만드는 데 쓰는 기록을 모은 것이에요. 일기는 원문 그대로이고(AI에도 가리지 않고 보내요),
 * 메신저 요약과 원문은 암호화된 채로 담겨요(분석할 때 풀어요).
 * 메신저는 **날짜마다 그날을 담은 가장 최근 업로드**가 그날의 주인이에요(2026.11.28, 앱이 하루치씩 올리게 되면서 바꿨어요).
 * 그래서 같은 주에 월요일 · 수요일을 따로 올리면 둘 다 쓰이고, 같은 날을 다시 올리면 새 업로드만 쓰여요.
 * weeks는 구간의 주에서 날짜를 하나라도 가진 업로드의 주간 요약이라, 한 주에 여러 개일 수 있어요(from ~ to는 그 요약이 담은 날).
 * originals는 그 업로드들의 원문이고, dates는 이 구간에서 그 업로드가 주인인 날이에요(원문이 겹치지 않아요).
 * messengerDays는 이 구간에서 메신저 업로드가 주인인 날의 수예요. 하루 단위(DAY)는 그날 실제로 쓴 내 말이 있을 때만 1이에요.
 * dayLines는 이 구간의 날마다 업로드할 때 AI가 그날 내 말 전체를 보고 만든 한 줄 요약이에요(암호화된 채, 2026.11.28).
 * 말 조각만으로는 무슨 일이었는지 모를 때 맥락이 돼요.
 */
public record PersonaInput(PersonaRange range, List<DiaryEntry> diaries, List<WeekEntry> weeks, List<OriginalEntry> originals,
	int messengerDays, List<DayLine> dayLines) {

	public PersonaInput {
		dayLines = dayLines == null ? List.of() : List.copyOf(dayLines);
	}

	public PersonaInput(PersonaRange range, List<DiaryEntry> diaries, List<WeekEntry> weeks) {
		this(range, diaries, weeks, List.of(), 0, List.of());
	}

	public PersonaInput(PersonaRange range, List<DiaryEntry> diaries, List<WeekEntry> weeks, List<OriginalEntry> originals,
		int messengerDays) {
		this(range, diaries, weeks, originals, messengerDays, List.of());
	}

	/** 메신저로 올린 하루의 한 줄 요약(암호화한 값)이에요. */
	public record DayLine(LocalDate date, byte[] lineEnc) {
	}

	public record DiaryEntry(UUID id, LocalDate date, OffsetDateTime updatedAt, List<Emotion> emotions, String content) {
	}

	public record WeekEntry(UUID id, LocalDate weekStart, LocalDate from, LocalDate to, OffsetDateTime createdAt, byte[] summaryEnc,
		byte[] traitsEnc, byte[] sampleEnc) {
	}

	public record OriginalEntry(UUID uploadId, Set<LocalDate> dates, byte[] originalEnc) {
	}

	/** 메신저 요약이 있는 주의 수예요(한 주에 요약이 여럿이어도 한 주로 세요). */
	public int messengerWeeks() {
		return weeks.stream().map(WeekEntry::weekStart).collect(Collectors.toSet()).size();
	}

	public RecordLevel level() {
		return RecordLevel.of(range.scope(), diaries.size(), messengerWeeks(), messengerDays);
	}
}
