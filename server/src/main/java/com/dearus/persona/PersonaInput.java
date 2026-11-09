package com.dearus.persona;

import com.dearus.diary.Emotion;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 한 시점을 만드는 데 쓰는 기록을 모은 것이에요. 일기는 원문 그대로이고(AI에도 가리지 않고 보내요),
 * 메신저 요약과 원문은 암호화된 채로 담겨요(분석할 때 풀어요). 한 주에 요약이 여럿이면(같은 주를 여러 번 올림) 가장 최근 하나만 있어요.
 * originals는 고른 주의 요약을 만든 업로드의 내 말 원문이에요. weekStarts는 그 업로드에서 고른 주라서,
 * 같은 주를 다른 업로드에서도 올렸어도 원문이 겹치지 않아요.
 */
public record PersonaInput(PersonaRange range, List<DiaryEntry> diaries, List<WeekEntry> weeks, List<OriginalEntry> originals) {

	public PersonaInput(PersonaRange range, List<DiaryEntry> diaries, List<WeekEntry> weeks) {
		this(range, diaries, weeks, List.of());
	}

	public record DiaryEntry(UUID id, LocalDate date, OffsetDateTime updatedAt, List<Emotion> emotions, String content) {
	}

	public record WeekEntry(UUID id, LocalDate weekStart, OffsetDateTime createdAt, byte[] summaryEnc, byte[] traitsEnc,
		byte[] sampleEnc) {
	}

	public record OriginalEntry(UUID uploadId, Set<LocalDate> weekStarts, byte[] originalEnc) {
	}

	public RecordLevel level() {
		return RecordLevel.of(range.scope(), diaries.size(), weeks.size());
	}
}
