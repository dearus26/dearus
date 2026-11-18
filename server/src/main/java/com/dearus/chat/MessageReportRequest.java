package com.dearus.chat;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** API 명세의 MessageReportRequest. detail은 선택이고, reason이 OTHER면 꼭 있어야 해요(검사는 {@link MessageReportService}). */
public record MessageReportRequest(
	@NotNull(message = "꼭 필요해요") MessageReportReason reason,
	@Size(max = 500, message = "500자까지 쓸 수 있어요") String detail) {
}
