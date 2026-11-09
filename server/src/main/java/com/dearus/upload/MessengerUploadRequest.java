package com.dearus.upload;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * API 명세의 MessengerUploadRequest. txt에서 앱이 추린 내 말 글자만 와요(가리지 않은 원문).
 * 예전의 photoCount · maskedCount · userMaskedCount는 2026.11.8에 지웠어요(모르는 필드라 보내도 무시해요).
 * consent가 true가 아니면 400 CONSENT_REQUIRED라서 null도 받아서 서비스에서 확인해요.
 */
public record MessengerUploadRequest(
	@NotNull(message = "꼭 필요해요") MessengerSource source,
	Boolean consent,
	@NotNull(message = "꼭 필요해요") @Size(min = 1, max = 5000, message = "메시지는 1개 이상 5000개 이하예요")
	@Valid List<MessengerMessage> messages) {
}
