package com.dearus.auth;

import com.dearus.common.Kst;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

	@Column(name = "notifications_opt_in", nullable = false)
	private boolean notificationsOptIn;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	/** 서비스 이용 동의 화면을 아직 거치지 않았어요. (한 번 동의했다가 철회한 사람은 해당하지 않아요) */
	public boolean needsConsent() {
		return aiConsentAt == null;
	}

	/** AI 분석 동의가 유효해요. 카톡 업로드 · 대화 · 리포트처럼 AI를 부르는 기능은 이 값이 true일 때만 써요. */
	public boolean aiConsentActive() {
		return aiConsentAt != null && aiRevokedAt == null;
	}

	/**
	 * 필수 동의 3개를 받았어요. 처음이거나 철회한 뒤 다시 동의하면 시각을 새로 기록하고,
	 * 이미 유효하면 시각은 그대로 둬요. notifications가 null이면 알림 설정은 그대로예요.
	 */
	public void agree(Boolean notifications) {
		if (!aiConsentActive()) {
			aiConsentAt = Kst.now();
			aiRevokedAt = null;
		}
		if (notifications != null) {
			notificationsOptIn = notifications;
		}
	}

	/** AI 분석 동의를 철회해요. 이미 철회했거나 동의한 적이 없으면 그대로 둬요. */
	public void revokeAiConsent() {
		if (aiConsentActive()) {
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
