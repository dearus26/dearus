package com.dearus.upload;

import com.dearus.common.Kst;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * messenger_uploads 테이블 (ERD). 업로드 한 건의 개수와 상태만 담고, 사진이나 글자는 담지 않아요.
 * 요약이 끝나면 DONE, 실패하면 FAILED(+ failureCode)예요.
 */
@Entity
@Table(name = "messenger_uploads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessengerUpload {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(nullable = false, updatable = false, length = 20)
	@Enumerated(EnumType.STRING)
	private MessengerSource source;

	@Column(name = "uploaded_at", nullable = false, updatable = false)
	private OffsetDateTime uploadedAt;

	@Column(nullable = false, length = 20)
	@Enumerated(EnumType.STRING)
	private MessengerStatus status;

	@Column(name = "failure_code", length = 40)
	private String failureCode;

	@Column(name = "photo_count", nullable = false, updatable = false)
	private int photoCount;

	@Column(name = "period_start")
	private LocalDate periodStart;

	@Column(name = "period_end")
	private LocalDate periodEnd;

	@Column(name = "masked_count", nullable = false, updatable = false)
	private int maskedCount;

	@Column(name = "user_masked_count", nullable = false, updatable = false)
	private int userMaskedCount;

	@Column(name = "server_masked_count")
	private Integer serverMaskedCount;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private OffsetDateTime expiresAt;

	/** 받은 직후의 업로드예요. 업로드 1년 뒤에 요약이 지워져요. */
	public static MessengerUpload receive(UUID userId, MessengerSource source, int photoCount, int maskedCount, int userMaskedCount) {
		MessengerUpload upload = new MessengerUpload();
		upload.userId = userId;
		upload.source = source;
		upload.uploadedAt = Kst.now();
		upload.status = MessengerStatus.PROCESSING;
		upload.photoCount = photoCount;
		upload.maskedCount = maskedCount;
		upload.userMaskedCount = userMaskedCount;
		upload.expiresAt = upload.uploadedAt.plusYears(1);
		return upload;
	}

	public void complete(LocalDate periodStart, LocalDate periodEnd, int serverMaskedCount) {
		this.status = MessengerStatus.DONE;
		this.failureCode = null;
		this.periodStart = periodStart;
		this.periodEnd = periodEnd;
		this.serverMaskedCount = serverMaskedCount;
	}

	public void fail(String failureCode) {
		this.status = MessengerStatus.FAILED;
		this.failureCode = failureCode;
	}

	public boolean isProcessing() {
		return status == MessengerStatus.PROCESSING;
	}
}
