package com.dearus.diary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** API 명세의 DiaryUpdate. 날짜는 바꿀 수 없어요. */
public record DiaryUpdateRequest(
	@NotBlank(message = "비어 있을 수 없어요") @Size(max = 5000, message = "5000자까지 쓸 수 있어요") String content,
	@Size(max = 7, message = "7개까지 고를 수 있어요") Set<Emotion> emotions) {
}
