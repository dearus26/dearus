package com.dearus.couple;

import com.dearus.auth.User;
import com.dearus.auth.UserRepository;
import com.dearus.chat.ChatSession;
import com.dearus.chat.ChatSessionRepository;
import com.dearus.chat.ChatStatus;
import com.dearus.common.Kst;
import com.dearus.common.ai.AiException;
import com.dearus.persona.PersonaScope;
import com.dearus.report.Report;
import com.dearus.report.ReportRepository;
import com.dearus.report.ReportStatus;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 두 시점 비교(GET /couples/comparisons)와 두 사람의 간극 만들기예요.
 * 같은 날(anchorDate · scope)마다 각 사람의 가장 최근 커플 세션을 쓰고, 내 말이 2개 미만이라 리포트가 없는(NONE) 세션은 세지 않아요.
 * 두 사람의 리포트가 모두 READY가 되면 간극을 AI로 한 번 만들어 두 리포트에 같은 값으로 넣어요. 간극이 두 리포트에서 같아야 READY예요
 * (한쪽이 같은 날 다시 대화하면 새 리포트에는 간극이 없어서 다시 만들어요).
 * 간극 만들기는 뒤에서 하고(reportExecutor), 같은 쌍은 한 번에 하나만, 실패하면 {@link #RETRY_AFTER} 뒤에 다시 해요(서버 한 대 가정, 메모리).
 * AI를 부르는 동안에는 DB 연결을 잡지 않아요. 로그에는 글 없이 세션 ID만 남겨요.
 */
@Service
public class CoupleComparisonService {

	private static final Logger log = LoggerFactory.getLogger(CoupleComparisonService.class);

	/** 간극 만들기에 실패하면 이만큼 지난 뒤에 다시 해요(앱이 1~2초마다 조회해도 AI를 계속 부르지 않게요). */
	static final Duration RETRY_AFTER = Duration.ofSeconds(30);

	/** 같은 날의 한 사람 쪽이에요. report는 세션이 끝났을 때만 있어요. */
	record Side(ChatSession session, Report report) {

		boolean ended() {
			return session.isEnded();
		}

		boolean reportReady() {
			return report != null && report.getStatus() == ReportStatus.READY;
		}
	}

	private final CoupleAccess access;
	private final ChatSessionRepository sessions;
	private final ReportRepository reports;
	private final UserRepository users;
	private final GapGenerator gaps;
	private final TransactionTemplate tx;
	private final Executor executor;
	private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
	private final Map<String, OffsetDateTime> lastFailure = new ConcurrentHashMap<>();

	public CoupleComparisonService(CoupleAccess access, ChatSessionRepository sessions, ReportRepository reports, UserRepository users,
		GapGenerator gaps, TransactionTemplate tx, @Qualifier("reportExecutor") Executor executor) {
		this.access = access;
		this.sessions = sessions;
		this.reports = reports;
		this.users = users;
		this.gaps = gaps;
		this.tx = tx;
		this.executor = executor;
	}

	/** GET /couples/comparisons — 열려 있어야 해요(연결됨 + 양측 동의 + 철회 없음). */
	public CoupleComparisonResponse compare(UUID userId, LocalDate anchorDate, PersonaScope scope) {
		CoupleAccess.Link link = access.requireOpen(userId);
		short window = (short) scope.windowDays();
		Side[] sides = tx.execute(status -> new Side[] {
			latestCounted(userId, link.coupleId(), anchorDate, window),
			latestCounted(link.partner().getId(), link.coupleId(), anchorDate, window)});
		Side mine = sides[0];
		Side theirs = sides[1];
		String state = status(mine, theirs);
		if ("GENERATING".equals(state) && mine.reportReady() && theirs.reportReady()) {
			requestGap(link.me(), mine.report(), link.partner(), theirs.report());
		}

		boolean mineEnded = mine != null && mine.ended();
		CoupleComparisonResponse.PastSelf partnerPastSelf = mineEnded && mine.reportReady()
			? new CoupleComparisonResponse.PastSelf(mine.report().getMemorySummary()) : null;
		CoupleComparisonResponse.PastSelf myPastSelf = mineEnded && theirs != null && theirs.reportReady()
			? new CoupleComparisonResponse.PastSelf(theirs.report().getMemorySummary()) : null;
		return new CoupleComparisonResponse(state, anchorDate, scope, mine == null ? null : mine.session().getId(),
			link.partner().getNickname(), partnerPastSelf, myPastSelf,
			"READY".equals(state) ? mine.report().getGapSummary() : null);
	}

	/** 화면 상태예요. 순수 함수라서 시험하기 쉬워요. */
	static String status(Side mine, Side theirs) {
		if (mine == null) {
			return "NOT_STARTED";
		}
		if (!mine.ended()) {
			return "IN_PROGRESS";
		}
		if (theirs == null || !theirs.ended()) {
			return "WAITING_PARTNER";
		}
		if (mine.reportReady() && theirs.reportReady() && mine.report().getGapSummary() != null
			&& Objects.equals(mine.report().getGapSummary(), theirs.report().getGapSummary())) {
			return "READY";
		}
		return "GENERATING";
	}

	/**
	 * 커플 세션의 리포트가 READY가 된 직후에 불러요(ReportService). 같은 날 연인의 리포트도 READY이고 이 세션이 내 가장 최근 세션이면 간극을 만들어요.
	 * 어떤 예외도 밖으로 내보내지 않아요(리포트는 이미 저장됐어요).
	 */
	public void onReportReady(UUID chatId) {
		try {
			Object[] pair = tx.execute(status -> {
				ChatSession session = sessions.findById(chatId).orElse(null);
				if (session == null || session.getCoupleId() == null) {
					return null;
				}
				short window = (short) session.scope().windowDays();
				Side mine = latestCounted(session.getUserId(), session.getCoupleId(), session.getAnchorDate(), window);
				Side theirs = latestCounted(session.getPersonaOwnerId(), session.getCoupleId(), session.getAnchorDate(), window);
				if (mine == null || !mine.session().getId().equals(chatId) || !mine.reportReady() || theirs == null || !theirs.reportReady()) {
					return null;
				}
				User me = users.findById(session.getUserId()).orElse(null);
				User partner = users.findById(session.getPersonaOwnerId()).orElse(null);
				return me == null || partner == null ? null : new Object[] {me, mine.report(), partner, theirs.report()};
			});
			if (pair != null) {
				requestGap((User) pair[0], (Report) pair[1], (User) pair[2], (Report) pair[3]);
			}
		} catch (RuntimeException e) {
			log.error("간극 만들기를 확인하지 못했어요 sessionId={}", chatId, e);
		}
	}

	/** 같은 날 한 사람의 가장 최근 세션이에요. 준비에 실패한 세션과, 끝났지만 리포트가 없는(NONE) 세션은 건너뛰어요. */
	private Side latestCounted(UUID userId, UUID coupleId, LocalDate anchorDate, short window) {
		for (ChatSession session : sessions.findByUserIdAndCoupleIdAndAnchorDateAndWindowDaysOrderByStartedAtDesc(userId, coupleId, anchorDate, window)) {
			if (!session.isEnded()) {
				if (session.getStatus() == ChatStatus.FAILED) {
					continue;
				}
				return new Side(session, null);
			}
			Report report = reports.findBySessionId(session.getId()).orElse(null);
			if (report != null && report.getStatus() == ReportStatus.NONE) {
				continue;
			}
			return new Side(session, report);
		}
		return null;
	}

	/**
	 * 간극을 뒤에서 만들어요. 두 사람의 AI 분석 동의가 모두 있어야 해요(두 사람의 이야기가 AI로 가요).
	 * 같은 쌍을 이미 만드는 중이거나 최근에 실패했으면 건너뛰어요.
	 */
	private void requestGap(User me, Report mine, User partner, Report theirs) {
		if (!me.aiConsentActive() || !partner.aiConsentActive()) {
			return;
		}
		String key = mine.getId().compareTo(theirs.getId()) < 0 ? mine.getId() + "|" + theirs.getId() : theirs.getId() + "|" + mine.getId();
		OffsetDateTime failedAt = lastFailure.get(key);
		if (failedAt != null && failedAt.isAfter(Kst.now().minus(RETRY_AFTER))) {
			return;
		}
		if (!inFlight.add(key)) {
			return;
		}
		// theirs는 연인이 그날의 나와 나눈 대화라서 "그날의 나" 이야기이고, mine은 "그날의 연인" 이야기예요
		GapGenerator.Prompt prompt = new GapGenerator.Prompt(me.getNickname(), theirs.getMemorySummary(), partner.getNickname(), mine.getMemorySummary());
		UUID mineId = mine.getId();
		UUID theirsId = theirs.getId();
		try {
			executor.execute(() -> generateGap(key, prompt, mineId, theirsId));
		} catch (RejectedExecutionException e) {
			inFlight.remove(key);
			log.warn("간극 만들기를 지금 시작하지 못했어요(다음 조회 때 다시 해요) reports={}", key);
		}
	}

	private void generateGap(String key, GapGenerator.Prompt prompt, UUID mineId, UUID theirsId) {
		try {
			String gap = gaps.generate(prompt); // AI 호출. 이 동안 DB 연결은 잡지 않아요
			tx.executeWithoutResult(status -> {
				Report mine = reports.findById(mineId).orElse(null);
				Report theirs = reports.findById(theirsId).orElse(null);
				if (mine == null || theirs == null || mine.getStatus() != ReportStatus.READY || theirs.getStatus() != ReportStatus.READY) {
					return; // 그 사이 철회 · 탈퇴로 지워졌어요
				}
				OffsetDateTime now = Kst.now();
				mine.setGap(gap, now);
				theirs.setGap(gap, now);
				reports.save(mine);
				reports.save(theirs);
			});
			lastFailure.remove(key);
			log.info("간극을 만들었어요 reports={}", key);
		} catch (AiException e) {
			lastFailure.put(key, Kst.now());
			log.warn("간극 만들기 실패 reports={} reason={} detail={}", key, e.reason(), e.getMessage());
		} catch (RuntimeException e) {
			lastFailure.put(key, Kst.now());
			log.error("간극 만들기 중 예상하지 못한 오류 reports={}", key, e);
		} finally {
			inFlight.remove(key);
		}
	}
}
