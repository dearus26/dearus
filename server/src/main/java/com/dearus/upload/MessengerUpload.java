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
import java.time.Period;
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

	/** 스크린샷으로 올렸을 때의 이미지 장수였어요. 2026.11.11부터 txt만 받아서 늘 0이에요(API · DB를 그대로 두려고 남겼어요). */
	@Column(name = "photo_count", nullable = false, updatable = false)
	private int photoCount;

	@Column(name = "period_start")
	private LocalDate periodStart;

	@Column(name = "period_end")
	private LocalDate periodEnd;

	/**
	 * 원문과 요약이 지워지는 시각이에요. 받을 때는 업로드 1년 뒤로 두고, 요약이 끝나면(complete) 대화한 날 기준으로 당겨요(2026.11.28).
	 */
	@Column(name = "expires_at", nullable = false)
	private OffsetDateTime expiresAt;

	/**
	 * 대화한 마지막 날 뒤로 원문을 더 두는 기간이에요. 과거의 나는 365일 전까지 고를 수 있고 "1년 전" 바로가기가 전후 2주를 써서,
	 * 대화한 날로부터 1년 2주가 지나면 그날의 말은 분석에 다시 쓰이지 않아요.
	 */
	static final Period KEEP_AFTER_CHAT = Period.ofYears(1).plusDays(14);

	/** 받은 직후의 업로드예요. txt만 받아서 photoCount는 0이에요. 지워지는 시각은 요약이 끝날 때 정해져요(complete). */
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

	/** 요약이 끝났어요. 지워지는 시각을 대화한 날 기준으로 정해요(expiryFor). 요약 행도 이 시각을 그대로 써요. */
	public void complete(LocalDate periodStart, LocalDate periodEnd) {
		this.status = MessengerStatus.DONE;
		this.failureCode = null;
		this.periodStart = periodStart;
		this.periodEnd = periodEnd;
		this.expiresAt = expiryFor(uploadedAt, periodEnd);
	}

	/**
	 * 원문과 요약이 지워지는 시각(2026.11.28 결정): 대화한 마지막 날 + 1년 2주가 지난 자정(한국 시간)이에요.
	 * 다만 올린 날부터 1년보다 오래 두지는 않아요(방침의 "최대 1년" 약속). 오래된 날의 대화를 올릴수록 일찍 지워져요.
	 */
	static OffsetDateTime expiryFor(OffsetDateTime uploadedAt, LocalDate lastChatDate) {
		OffsetDateTime byChat = lastChatDate.plus(KEEP_AFTER_CHAT).plusDays(1).atStartOfDay(Kst.ZONE).toOffsetDateTime();
		OffsetDateTime byUpload = uploadedAt.plusYears(1);
		return byChat.isBefore(byUpload) ? byChat : byUpload;
	}

	public void fail(String failureCode) {
		this.status = MessengerStatus.FAILED;
		this.failureCode = failureCode;
	}

	/** API의 format이에요. 지금은 늘 TXT이고, IMAGE는 2026.11.11 전에 스크린샷으로 올린 업로드에만 있어요. */
	public MessengerFormat format() {
		return photoCount > 0 ? MessengerFormat.IMAGE : MessengerFormat.TXT;
	}

	public boolean isProcessing() {
		return status == MessengerStatus.PROCESSING;
	}
}
