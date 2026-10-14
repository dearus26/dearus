package com.dearus.diary;

import com.dearus.common.Kst;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** API 명세의 Diary. 감정 태그는 항상 같은 순서(선언 순서)로 내려가요. */
public record DiaryResponse(
	UUID id,
	LocalDate date,
	String content,
	List<Emotion> emotions,
	OffsetDateTime createdAt,
	OffsetDateTime updatedAt) {

	public static DiaryResponse from(Diary diary) {
		return new DiaryResponse(
			diary.getId(),
			diary.getDiaryDate(),
			diary.getContent(),
			diary.getEmotions().stream().sorted().toList(),
			Kst.toKst(diary.getCreatedAt()),
			Kst.toKst(diary.getUpdatedAt()));
	}
}
