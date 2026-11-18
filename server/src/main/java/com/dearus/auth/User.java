package com.dearus.auth;

import com.dearus.common.Kst;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** users 테이블 (ERD). 같은 소셜 계정(provider + provider_user_id)은 같은 사용자예요. */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, length = 20)
	private String provider;

	@Column(name = "provider_user_id", nullable = false)
	private String providerUserId;

	@Column(nullable = false, length = 50)
	private String nickname;

	@Column(name = "ai_consent_at")
	private OffsetDateTime aiConsentAt;

	@Column(name = "ai_revoked_at")
	private OffsetDateTime aiRevokedAt;

	/** 민감정보 처리 동의 시각(V10, 2026.11.18). 필수 동의라서 agree에서 함께 받아요. */
	@Column(name = "sensitive_consent_at")
	private OffsetDateTime sensitiveConsentAt;

	/** 생년월일(V10). 처음 동의할 때 한 번만 받고 바꾸지 않아요. */
	@Column(name = "birth_date")
	private LocalDate birthDate;

	@Column(name = "notifications_opt_in", nullable = false)
	private boolean notificationsOptIn;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	/**
	 * 서비스 이용 동의 화면을 거쳐야 해요. 동의한 적이 없거나, 민감정보 동의 · 생년월일이 아직 없으면(2026.11.18 전에 동의한 계정) 해당해요.
	 * 모두 받은 뒤 AI 분석 동의만 철회한 사람은 해당하지 않아요.
	 */
	public boolean needsConsent() {
		return aiConsentAt == null || sensitiveConsentAt == null || birthDate == null;
	}

	/**
	 * AI 분석 동의와 민감정보 동의가 모두 유효해요. 카톡 업로드 · 대화 · 리포트처럼 AI를 부르는 기능은 이 값이 true일 때만 써요.
	 * 일기 · 대화에 든 민감정보가 AI로 가서 민감정보 동의도 함께 봐요.
	 */
	public boolean aiConsentActive() {
		return aiConsentAt != null && aiRevokedAt == null && sensitiveConsentAt != null;
	}

	/**
	 * 필수 동의(이용약관 · 개인정보 · AI 분석 · 민감정보)를 받았어요. 처음이거나 철회한 뒤 다시 동의하면 AI 동의 시각을 새로 기록하고,
	 * 이미 유효하면 시각은 그대로 둬요. 민감정보 동의 시각은 처음 한 번만 기록해요. notifications가 null이면 알림 설정은 그대로예요.
	 * 생년월일 확인은 {@link ConsentService}가 먼저 해요.
	 */
	public void agree(Boolean notifications) {
		if (aiConsentAt == null || aiRevokedAt != null) {
			aiConsentAt = Kst.now();
			aiRevokedAt = null;
		}
		if (sensitiveConsentAt == null) {
			sensitiveConsentAt = Kst.now();
		}
		if (notifications != null) {
			notificationsOptIn = notifications;
		}
	}

	/** 생년월일을 처음 한 번만 기록해요. 이미 있으면 바꾸지 않아요. */
	public void recordBirthDate(LocalDate date) {
		if (birthDate == null) {
			birthDate = date;
		}
	}

	/** AI 분석 동의를 철회해요. 이미 철회했거나 동의한 적이 없으면 그대로 둬요. */
	public void revokeAiConsent() {
		if (aiConsentAt != null && aiRevokedAt == null) {
			aiRevokedAt = Kst.now();
		}
	}

	public static User create(String provider, String providerUserId, String nickname) {
		User user = new User();
		user.provider = provider;
		user.providerUserId = providerUserId;
		user.nickname = nickname;
		user.createdAt = Kst.now();
		return user;
	}
}
