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
 * messenger_uploads 테이블 (ERD). 업로드 한 건의 형식 · 상태를 담아요. 사진은 담지 않아요.
 * 요약이 끝나면 DONE, 실패하면 FAILED(+ failureCode)예요.
 * 내 말 원문(original_enc, 암호화)은 이 엔티티에 매핑하지 않아요. 목록을 읽을 때마다 큰 값을 함께 읽지 않으려고
 * MessengerUploadRepository의 전용 쿼리로만 쓰고 읽어요.
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

	/** 이미지 장수예요. txt로 올리면 0이에요. */
	@Column(name = "photo_count", nullable = false, updatable = false)
	private int photoCount;

	@Column(name = "period_start")
	private LocalDate periodStart;

	@Column(name = "period_end")
	private LocalDate periodEnd;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private OffsetDateTime expiresAt;

	/** 받은 직후의 업로드예요. photoCount는 이미지 장수이고 txt는 0이에요. 업로드 1년 뒤에 원문과 요약이 지워져요. */
	public static MessengerUpload receive(UUID userId, MessengerSource source, int photoCount) {
		MessengerUpload upload = new MessengerUpload();
		upload.userId = userId;
		upload.source = source;
		upload.uploadedAt = Kst.now();
		upload.status = MessengerStatus.PROCESSING;
		upload.photoCount = photoCount;
		upload.expiresAt = upload.uploadedAt.plusYears(1);
		return upload;
	}

	public void complete(LocalDate periodStart, LocalDate periodEnd) {
		this.status = MessengerStatus.DONE;
		this.failureCode = null;
		this.periodStart = periodStart;
		this.periodEnd = periodEnd;
	}

	public void fail(String failureCode) {
		this.status = MessengerStatus.FAILED;
		this.failureCode = failureCode;
	}

	/** 이미지로 올렸는지 txt로 올렸는지 (API의 format). 이미지는 1장 이상이라 0이면 txt예요. */
	public MessengerFormat format() {
		return photoCount > 0 ? MessengerFormat.IMAGE : MessengerFormat.TXT;
	}

	public boolean isProcessing() {
		return status == MessengerStatus.PROCESSING;
	}
}
