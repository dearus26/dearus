package com.dearus.chat;

import com.dearus.common.Kst;
import com.dearus.persona.PersonaScope;
import com.dearus.persona.RecordLevel;
import com.dearus.persona.ShortcutKey;
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
 * chat_sessions 테이블 (ERD). 과거의 나와 한 대화 한 번이에요.
 * PREPARING으로 시작해서 시점 분석이 끝나면 READY, 실패하면 FAILED(+ failureCode)가 돼요.
 * 기록이 전혀 없으면 분석 없이 analysisId가 빈 채로 바로 READY예요.
 */
@Entity
@Table(name = "chat_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatSession {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	/** 페르소나 주인. 지금은 항상 userId와 같아요. */
	@Column(name = "persona_owner_id", nullable = false, updatable = false)
	private UUID personaOwnerId;

	/** 커플 모드의 같은 날 비교 세션이면 커플 ID. 커플 기능을 만들기 전에는 항상 비어 있어요. */
	@Column(name = "couple_id", updatable = false)
	private UUID coupleId;

	@Column(name = "analysis_id")
	private UUID analysisId;

	@Column(name = "anchor_date", nullable = false, updatable = false)
	private LocalDate anchorDate;

	@Column(name = "window_days", nullable = false, updatable = false)
	private short windowDays;

	@Column(name = "shortcut_key", updatable = false, length = 20)
	@Enumerated(EnumType.STRING)
	private ShortcutKey shortcutKey;

	@Column(nullable = false, length = 20)
	@Enumerated(EnumType.STRING)
	private ChatStatus status;

	@Column(name = "failure_code", length = 40)
	private String failureCode;

	@Column(name = "record_level", nullable = false, updatable = false, length = 20)
	@Enumerated(EnumType.STRING)
	private RecordLevel recordLevel;

	@Column(name = "started_at", nullable = false, updatable = false)
	private OffsetDateTime startedAt;

	@Column(name = "ended_at")
	private OffsetDateTime endedAt;

	/** 방금 만든 세션이에요(PREPARING). 분석이 필요 없으면 바로 {@link #ready}로 넘겨요. */
	public static ChatSession start(UUID userId, LocalDate anchorDate, PersonaScope scope, ShortcutKey shortcutKey,
		RecordLevel recordLevel) {
		ChatSession session = new ChatSession();
		session.userId = userId;
		session.personaOwnerId = userId;
		session.anchorDate = anchorDate;
		session.windowDays = (short) scope.windowDays();
		session.shortcutKey = shortcutKey;
		session.status = ChatStatus.PREPARING;
		session.recordLevel = recordLevel;
		session.startedAt = Kst.now();
		return session;
	}

	/** 대화할 수 있어요. analysisId가 null이면 기록이 없어서 기본 말투로 대화해요. */
	public void ready(UUID analysisId) {
		this.status = ChatStatus.READY;
		this.analysisId = analysisId;
		this.failureCode = null;
	}

	public void fail(String failureCode) {
		this.status = ChatStatus.FAILED;
		this.failureCode = failureCode;
	}

	public PersonaScope scope() {
		return windowDays == 0 ? PersonaScope.DAY : PersonaScope.AROUND;
	}

	public boolean isPreparing() {
		return status == ChatStatus.PREPARING;
	}
}
