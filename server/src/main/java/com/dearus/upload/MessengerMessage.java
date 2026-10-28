package com.dearus.upload;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** API 명세의 MessengerMessage. 앱이 폰 안에서 가린 내 말풍선 글자 하나예요. */
public record MessengerMessage(
	@NotNull(message = "꼭 필요해요") LocalDate date,
	@NotBlank(message = "비어 있을 수 없어요") @Size(max = 2000, message = "한 메시지는 2000자까지예요") String text) {
}
