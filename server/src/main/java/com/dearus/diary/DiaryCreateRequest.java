package com.dearus.diary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;

/** API 명세의 DiaryCreate. 감정 태그는 없어도 저장돼요. */
public record DiaryCreateRequest(
	@NotNull(message = "꼭 필요해요") LocalDate date,
	@NotBlank(message = "비어 있을 수 없어요") @Size(max = 5000, message = "5000자까지 쓸 수 있어요") String content,
	@Size(max = 7, message = "7개까지 고를 수 있어요") Set<Emotion> emotions) {
}
