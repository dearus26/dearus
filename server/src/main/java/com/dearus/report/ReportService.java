package com.dearus.report;

import com.dearus.auth.User;
import com.dearus.auth.UserRepository;
import com.dearus.chat.ChatDuration;
import com.dearus.chat.ChatMessage;
import com.dearus.chat.ChatMessageRepository;
import com.dearus.chat.ChatReplyService;
import com.dearus.chat.ChatRole;
import com.dearus.chat.ChatSession;
import com.dearus.chat.ChatSessionRepository;
import com.dearus.chat.ChatStatus;
import com.dearus.common.ApiException;
import com.dearus.common.Kst;
import com.dearus.common.ai.AiException;
import com.dearus.couple.CoupleAccess;
import com.dearus.couple.CoupleComparisonService;
import com.dearus.persona.PersonaAnalysisService;
import com.dearus.persona.PersonaContent;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 대화를 끝내고(POST /chats/{id}/end) 회고 리포트를 만들어요(GET /chats/{id}/report).
 * 끝내기는 바로 돌아오고(ended_at 기록 + reports 행 만들기), AI가 리포트를 만드는 일은 뒤에서 해요(reportExecutor).
 * 규칙:
 * - 서버는 대화를 자동으로 끝내지 않아요. 끝내기를 눌러야 종료되고 리포트가 만들어져요.
 * - 내 말이 {@link #MIN_USER_MESSAGES}개 미만이면 끝낼 수는 있지만 리포트는 만들지 않아요(NONE).
 * - 이미 끝난 대화에 다시 불러도 같은 결과예요. 리포트가 FAILED이면 다시 만들어요(재시도).
 * - 끝내는 동안에는 같은 세션의 답변 잠금을 잡아서, 마지막 답변이 저장되기 전에 끝나거나 끝나는 중에 새 말이 들어오지 않아요.
 * - AI를 부르는 동안에는 DB 연결을 잡지 않아요. 내 말은 대화와 같이 가리지 않은 원문 그대로 보내요.
 * - GENERATING이 {@link #STALE_AFTER}를 넘으면 실패로 봐요(서버가 다시 켜져서 작업이 사라진 경우).
 * 서버 한 대를 가정한 잠금이에요. 서버가 여러 대가 돼도 reports.session_id의 유일 키가 중복 생성을 막아요.
 * 커플 모드 세션은 "그날의 연인에게서 들은 이야기"를 만들고, 끝낼 때 커플 접근을 다시 확인해요. 리포트가 READY가 되면
 * 같은 날 연인의 리포트와 짝지어 두 사람의 간극을 만들도록 알려요(CoupleComparisonService).
 * 로그에는 글자 없이 세션 ID와 이유만 남겨요.
 */
@Service
public class ReportService {

	private static final Logger log = LoggerFactory.getLogger(ReportService.class);

	/** 리포트를 만들려면 필요한 내 말의 최소 개수 */
	static final int MIN_USER_MESSAGES = 2;
	/** GENERATING이 이 시간을 넘으면 실패로 봐요 */
	static final Duration STALE_AFTER = Duration.ofMinutes(10);
	/** AI로 보내는 대화의 최대 메시지 수(가장 최근 것부터) */
	static final int MAX_CONVERSATION_MESSAGES = 120;

	/** 끝내기 결과와, 리포트를 만들 작업을 시작해야 하는지예요. */
	private record Ended(EndChatResult result, boolean generate) {
	}

	/** AI로 보낼 내용이에요. 개인 모드면 personal, 커플 모드면 couple 하나만 있어요. */
	private record Work(ReportGenerator.Prompt personal, ReportGenerator.CouplePrompt couple) {
	}

	private final ChatSessionRepository sessions;
	private final ChatMessageRepository messages;
	private final UserRepository users;
	private final ReportRepository reports;
	private final PersonaAnalysisService personas;
	private final ReportGenerator generator;
	private final ChatReplyService replies;
	private final CoupleAccess couples;
	private final CoupleComparisonService comparisons;
	private final TransactionTemplate tx;
	private final Executor executor;

	public ReportService(ChatSessionRepository sessions, ChatMessageRepository messages, UserRepository users, ReportRepository reports,
		PersonaAnalysisService personas, ReportGenerator generator, ChatReplyService replies, CoupleAccess couples,
		CoupleComparisonService comparisons, TransactionTemplate tx,
		@Qualifier("reportExecutor") Executor executor) {
		this.sessions = sessions;
		this.messages = messages;
		this.users = users;
		this.reports = reports;
		this.personas = personas;
		this.generator = generator;
		this.replies = replies;
		this.couples = couples;
		this.comparisons = comparisons;
		this.tx = tx;
		this.executor = executor;
	}

	/** POST /chats/{chatId}/end */
	public EndChatResult end(UUID userId, UUID chatId) {
		ChatSession session = sessions.findByIdAndUserId(chatId, userId).orElseThrow(ApiException::notFound);
		User user = users.findById(userId).orElseThrow(ApiException::unauthorized);
		if (!user.aiConsentActive()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "AI_CONSENT_REQUIRED", "AI 분석에 동의해야 쓸 수 있어요.");
		}
		couples.requireForSession(session); // 커플 세션이면 리포트에 연인의 분석이 들어가서 접근을 다시 확인해요(아니면 403)
		if (session.getStatus() != ChatStatus.READY) {
			throw new ApiException(HttpStatus.CONFLICT, "CHAT_NOT_READY", "아직 끝낼 수 없는 대화예요.");
		}
		if (!replies.tryLock(chatId)) {
			throw new ApiException(HttpStatus.CONFLICT, "CHAT_BUSY", "답변이 아직 끝나지 않았어요. 잠시 뒤에 다시 눌러 주세요.");
		}
		Ended ended;
		try {
			try {
				ended = tx.execute(status -> finish(chatId));
			} catch (DataIntegrityViolationException e) {
				// 다른 서버가 먼저 끝내면서 리포트 행을 만들었어요. 그 결과를 그대로 돌려줘요.
				ended = tx.execute(status -> existing(chatId));
			}
		} finally {
			replies.unlock(chatId);
		}
		if (ended.generate()) {
			dispatch(chatId);
		}
		return ended.result();
	}

	/** 한 트랜잭션에서 끝낸 시각을 기록하고 reports 행을 만들거나(없을 때) 실패한 것을 다시 만들기로 바꿔요. */
	private Ended finish(UUID chatId) {
		ChatSession session = sessions.findById(chatId).orElseThrow(ApiException::notFound);
		OffsetDateTime now = Kst.now();
		session.end(now);

		Report report = reports.findBySessionId(chatId).orElse(null);
		boolean generate = false;
		if (report == null) {
			List<ChatMessage> all = messages.findBySessionIdOrderBySeqAsc(chatId);
			long mine = all.stream().filter(m -> m.getRole() == ChatRole.USER).count();
			int minutes = ChatDuration.minutes(ChatDuration.total(all.stream().map(ChatMessage::getCreatedAt).toList()));
			report = reports.saveAndFlush(mine < MIN_USER_MESSAGES ? Report.none(chatId, minutes, now) : Report.generating(chatId, minutes, now));
			generate = report.getStatus() == ReportStatus.GENERATING;
		} else {
			if (report.isStale(STALE_AFTER)) {
				report.fail("REPORT_TIMEOUT", now);
			}
			if (report.getStatus() == ReportStatus.FAILED) {
				report.retry(now);
				generate = true;
			}
		}
		return new Ended(EndChatResult.of(chatId, session.getEndedAt(), report), generate);
	}

	private Ended existing(UUID chatId) {
		ChatSession session = sessions.findById(chatId).orElseThrow(ApiException::notFound);
		Report report = reports.findBySessionId(chatId).orElseThrow(ApiException::notFound);
		return new Ended(EndChatResult.of(chatId, session.getEndedAt(), report), false);
	}

	private void dispatch(UUID chatId) {
		try {
			executor.execute(() -> generate(chatId));
		} catch (RejectedExecutionException e) {
			log.error("리포트 만들기를 시작하지 못했어요 sessionId={}", chatId, e);
			failReport(chatId, "SERVER_BUSY");
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SERVER_BUSY", "지금 리포트를 만드는 요청이 많아요. 잠시 뒤에 다시 눌러 주세요.");
		}
	}

	/** 뒤에서 리포트를 만들어 저장해요. 실패하면 FAILED + 이유(AI_UNAVAILABLE 등)로 남겨요. 어떤 예외도 밖으로 내보내지 않아요. */
	public void generate(UUID chatId) {
		long started = System.nanoTime();
		try {
			Work work = tx.execute(status -> loadWork(chatId));
			if (work == null) {
				return; // 그 사이 지워졌어요(탈퇴 · 커플 철회)
			}
			// AI 호출. 이 동안 DB 연결은 잡지 않아요
			ReportContent content = work.personal() == null ? null : generator.generate(work.personal());
			CoupleReportContent coupleContent = work.couple() == null ? null : generator.generateCouple(work.couple());
			Boolean saved = tx.execute(status -> reports.findBySessionId(chatId)
				.filter(report -> report.getStatus() == ReportStatus.GENERATING)
				.map(report -> {
					if (coupleContent != null) {
						report.completeCouple(coupleContent.memorySummary(), coupleContent.oneLineSummary(), Kst.now());
					} else {
						report.complete(content, Kst.now());
					}
					reports.save(report);
					return true;
				}).orElse(false));
			log.info("리포트 완성 sessionId={} couple={} totalMs={}", chatId, coupleContent != null, (System.nanoTime() - started) / 1_000_000);
			if (coupleContent != null && Boolean.TRUE.equals(saved)) {
				comparisons.onReportReady(chatId); // 연인의 같은 날 리포트도 있으면 간극을 만들어요
			}
		} catch (AiException e) {
			log.warn("리포트 만들기 실패 sessionId={} reason={} detail={}", chatId, e.reason(), e.getMessage());
			failReport(chatId, e.reason().code());
		} catch (RuntimeException e) {
			log.error("리포트 만들기 중 예상하지 못한 오류 sessionId={}", chatId, e);
			failReport(chatId, "INTERNAL_ERROR");
		}
	}

	/** AI로 보낼 내용을 모아요. 내 말과 과거의 나(또는 그날의 연인)의 말(AI가 만든 글) 모두 저장된 원문 그대로예요. */
	private Work loadWork(UUID chatId) {
		ChatSession session = sessions.findById(chatId).orElse(null);
		if (session == null) {
			return null;
		}
		List<ChatMessage> recent = new ArrayList<>(messages.findBySessionIdOrderBySeqDesc(chatId, PageRequest.of(0, MAX_CONVERSATION_MESSAGES)));
		Collections.reverse(recent); // seq 오름차순
		PersonaContent persona = session.getAnalysisId() == null ? null : personas.contentOf(session.getAnalysisId()).orElse(null);
		List<ReportGenerator.Turn> turns = recent.stream()
			.map(message -> new ReportGenerator.Turn(message.getRole() == ChatRole.USER, message.getContent()))
			.toList();
		if (session.getCoupleId() != null) {
			String pastSelfName = users.findById(session.getPersonaOwnerId()).map(User::getNickname).orElse("연인");
			return new Work(null, new ReportGenerator.CouplePrompt(session.scope(), session.getAnchorDate(), persona, pastSelfName, turns));
		}
		return new Work(new ReportGenerator.Prompt(session.scope(), session.getAnchorDate(), persona, turns), null);
	}

	private void failReport(UUID chatId, String failureCode) {
		try {
			tx.executeWithoutResult(status -> reports.findBySessionId(chatId)
				.filter(report -> report.getStatus() == ReportStatus.GENERATING)
				.ifPresent(report -> {
					report.fail(failureCode, Kst.now());
					reports.save(report);
				}));
		} catch (RuntimeException e) {
			log.error("리포트 실패 상태를 저장하지 못했어요 sessionId={}", chatId, e);
		}
	}

	/**
	 * GET /chats/{chatId}/report. 만드는 중이면 빈 값(202)이에요. 오래된 GENERATING은 여기서 FAILED로 바꿔요
	 * (그 뒤 오류를 던져도 바뀐 상태가 남게 noRollbackFor를 둬요).
	 */
	@Transactional(noRollbackFor = ApiException.class)
	public Optional<ReportResponse> get(UUID userId, UUID chatId) {
		ChatSession session = sessions.findByIdAndUserId(chatId, userId).orElseThrow(ApiException::notFound);
		if (!session.isEnded()) {
			throw new ApiException(HttpStatus.NOT_FOUND, "CHAT_NOT_ENDED", "아직 끝나지 않은 대화예요.");
		}
		Report report = reports.findBySessionId(chatId).orElseThrow(ReportService::notAvailable);
		if (report.isStale(STALE_AFTER)) {
			report.fail("REPORT_TIMEOUT", Kst.now());
			reports.save(report);
		}
		return switch (report.getStatus()) {
			case GENERATING -> Optional.empty();
			case READY -> Optional.of(ReportResponse.from(session, report));
			case FAILED -> throw new ApiException(HttpStatus.BAD_GATEWAY, "REPORT_FAILED",
				"리포트를 만들지 못했어요. 대화 끝내기를 다시 눌러 주세요.");
			case NONE -> throw notAvailable();
		};
	}

	private static ApiException notAvailable() {
		return new ApiException(HttpStatus.NOT_FOUND, "REPORT_NOT_AVAILABLE", "이 대화는 이야기가 짧아서 리포트를 만들지 않았어요.");
	}
}
