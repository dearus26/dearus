package com.dearus.upload;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * API 명세의 MessengerUploadRequest. 사진은 없고 가린 글자만 와요.
 * consent가 true가 아니면 400 CONSENT_REQUIRED라서 null도 받아서 서비스에서 확인해요.
 */
public record MessengerUploadRequest(
	@NotNull(message = "꼭 필요해요") MessengerSource source,
	Boolean consent,
	@NotNull(message = "꼭 필요해요") @Min(value = 1, message = "1장 이상이어야 해요")
	@Max(value = 100, message = "100장까지 올릴 수 있어요") Integer photoCount,
	@NotNull(message = "꼭 필요해요") @Min(value = 0, message = "0 이상이어야 해요") Integer maskedCount,
	@NotNull(message = "꼭 필요해요") @Min(value = 0, message = "0 이상이어야 해요") Integer userMaskedCount,
	@NotNull(message = "꼭 필요해요") @Size(min = 1, max = 5000, message = "메시지는 1개 이상 5000개 이하예요")
	@Valid List<MessengerMessage> messages) {
}
