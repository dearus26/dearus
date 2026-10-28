package com.dearus.upload;

import com.dearus.common.Kst;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** API 명세의 MessengerUpload. weekCount · periodStart · periodEnd는 DONE일 때만 있어요. */
public record MessengerUploadResponse(
	UUID id,
	MessengerStatus status,
	OffsetDateTime uploadedAt,
	int photoCount,
	int maskedCount,
	int userMaskedCount,
	Integer serverMaskedCount,
	LocalDate periodStart,
	LocalDate periodEnd,
	Integer weekCount,
	OffsetDateTime expiresAt,
	String failureCode) {

	/** summaryCount는 저장한 주간 요약 수예요. DONE이 아니면 null이에요. */
	public static MessengerUploadResponse from(MessengerUpload upload, Integer summaryCount) {
		return new MessengerUploadResponse(
			upload.getId(),
			upload.getStatus(),
			Kst.toKst(upload.getUploadedAt()),
			upload.getPhotoCount(),
			upload.getMaskedCount(),
			upload.getUserMaskedCount(),
			upload.getServerMaskedCount(),
			upload.getPeriodStart(),
			upload.getPeriodEnd(),
			upload.getStatus() == MessengerStatus.DONE ? summaryCount : null,
			Kst.toKst(upload.getExpiresAt()),
			upload.getFailureCode());
	}
}
