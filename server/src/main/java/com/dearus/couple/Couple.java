package com.dearus.couple;

import com.dearus.common.Kst;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * couples 테이블 (ERD). 초대 코드를 만들면 PENDING으로 생기고, 상대가 코드를 입력하면 CONNECTED,
 * 철회 · 해제하면 DISCONNECTED예요. 코드는 만든 때부터 {@link #CODE_TTL} 동안 유효해요.
 */
@Entity
@Table(name = "couples")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Couple {

	/** 초대 코드의 유효 기간(스토리보드 가정: 24시간) */
	public static final Duration CODE_TTL = Duration.ofHours(24);

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "invite_code", nullable = false, length = 20)
	private String inviteCode;

	@Column(name = "code_expires_at", nullable = false)
	private OffsetDateTime codeExpiresAt;

	@Column(nullable = false, length = 20)
	@Enumerated(EnumType.STRING)
	private CoupleStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "connected_at")
	private OffsetDateTime connectedAt;

	@Column(name = "disconnected_at")
	private OffsetDateTime disconnectedAt;

	/** 방금 만든 초대예요. 상대가 코드를 입력하기를 기다려요. */
	public static Couple invite(String inviteCode, OffsetDateTime now) {
		Couple couple = new Couple();
		couple.inviteCode = inviteCode;
		couple.codeExpiresAt = now.plus(CODE_TTL);
		couple.status = CoupleStatus.PENDING;
		couple.createdAt = now;
		return couple;
	}

	/** 만료된 코드를 새 코드로 바꿔요(다시 24시간). */
	public void renewCode(String inviteCode, OffsetDateTime now) {
		this.inviteCode = inviteCode;
		this.codeExpiresAt = now.plus(CODE_TTL);
	}

	public boolean isCodeExpired(OffsetDateTime now) {
		return !codeExpiresAt.isAfter(now);
	}

	public void connect(OffsetDateTime now) {
		this.status = CoupleStatus.CONNECTED;
		this.connectedAt = now;
	}

	public void disconnect(OffsetDateTime now) {
		this.status = CoupleStatus.DISCONNECTED;
		this.disconnectedAt = now;
	}

	public boolean isPending() {
		return status == CoupleStatus.PENDING;
	}

	public boolean isConnected() {
		return status == CoupleStatus.CONNECTED;
	}

	/** 응답에는 한국 시간으로 내보내요. */
	public OffsetDateTime connectedAtKst() {
		return Kst.toKst(connectedAt);
	}
}
