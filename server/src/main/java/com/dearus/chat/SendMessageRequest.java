package com.dearus.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** API 명세의 SendMessage. 한 번에 2000자까지예요. */
public record SendMessageRequest(
	@NotBlank(message = "비어 있을 수 없어요") @Size(max = 2000, message = "한 번에 2000자까지 보낼 수 있어요") String content) {
}
