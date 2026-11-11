package com.dearus.couple;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * couple_members 테이블 (ERD). 커플 하나에 두 사람(코드를 만든 쪽, 입력한 쪽)이에요.
 * 각자 동의하면 consentedAt이 채워지고, 철회 · 해제하면 revokedAt이 채워져요. revokedAt이 비어 있는 행이 그 사람의 활성 커플이에요(한 사람에 하나).
 */
@Entity
@Table(name = "couple_members")
@IdClass(CoupleMemberId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoupleMember {

	@Id
	@Column(name = "couple_id", nullable = false, updatable = false)
	private UUID coupleId;

	@Id
	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(nullable = false, updatable = false, length = 10)
	@Enumerated(EnumType.STRING)
	private CoupleRole role;

	@Column(name = "joined_at", nullable = false, updatable = false)
	private OffsetDateTime joinedAt;

	@Column(name = "consented_at")
	private OffsetDateTime consentedAt;

	@Column(name = "revoked_at")
	private OffsetDateTime revokedAt;

	public static CoupleMember join(UUID coupleId, UUID userId, CoupleRole role, OffsetDateTime now) {
		CoupleMember member = new CoupleMember();
		member.coupleId = coupleId;
		member.userId = userId;
		member.role = role;
		member.joinedAt = now;
		return member;
	}

	/** 커플 모드 동의. 이미 동의했으면 처음 시각을 그대로 둬요. */
	public void consent(OffsetDateTime now) {
		if (consentedAt == null) {
			consentedAt = now;
		}
	}

	public void revoke(OffsetDateTime now) {
		if (revokedAt == null) {
			revokedAt = now;
		}
	}

	public boolean isConsented() {
		return consentedAt != null && revokedAt == null;
	}
}
