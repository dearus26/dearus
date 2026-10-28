package com.dearus.chat;

import com.dearus.auth.User;
import com.dearus.auth.UserRepository;
import com.dearus.common.ApiException;
import com.dearus.common.Kst;
import com.dearus.diary.Diary;
import com.dearus.diary.DiaryRepository;
import com.dearus.diary.Emotion;
import com.dearus.persona.PersonaAnalysisService;
import com.dearus.persona.PersonaAnalysisService.Lookup;
import com.dearus.persona.PersonaRange;
import com.dearus.persona.PersonaScope;
import com.dearus.persona.RecordLevel;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 대화 세션을 만들고 조회해요. 세션을 만들 때 DB만 읽어서 바로 정할 수 있으면(기록이 없거나 저장된 분석을 재사용) 바로 READY로
 * 첫 인사까지 저장하고, 분석이 필요하면 PREPARING으로 돌려준 뒤 뒤에서 분석해요(ChatPreparationJob).
 * 대화 주고받기(SSE) · 끝내기 · 리포트는 다음 단계예요.
 */
@Service
public class ChatService {

	private static final Logger log = LoggerFactory.getLogger(ChatService.class);

	/** PREPARING이 이 시간 안에 안 끝나면 실패로 봐요(서버가 다시 켜져서 작업이 사라진 경우 포함). */
	static final Duration STALE_AFTER = Duration.ofMinutes(10);
	/** "과거의 나" 목록에 보여주는 최대 개수 */
	static final int LIST_LIMIT = 50;
	static final int PREVIEW_MAX_CHARS = 60;

	private final UserRepository users;
	private final ChatSessionRepository sessions;
	private final ChatMessageRepository messages;
	private final DiaryRepository diaries;
	private final PersonaAnalysisService persona;
	private final ChatPreparationJob job;
	private final TransactionTemplate tx;
	private final Executor executor;

	public ChatService(UserRepository users, ChatSessionRepository sessions, ChatMessageRepository messages, DiaryRepository diaries,
		PersonaAnalysisService persona, ChatPreparationJob job, TransactionTemplate tx,
		@Qualifier("chatPrepareExecutor") Executor executor) {
		this.users = users;
		this.sessions = sessions;
		this.messages = messages;
		this.diaries = diaries;
		this.persona = persona;
		this.job = job;
		this.tx = tx;
		this.executor = executor;
	}

	/** POST /chats — 과거의 나와 대화 시작 */
	public ChatSessionResponse create(UUID userId, ChatCreateRequest request) {
		if (request.shortcutKey() != null && request.scope() != PersonaScope.AROUND) {
			throw ApiException.validation("shortcutKey", "바로가기는 전후 2주(AROUND)일 때만 보낼 수 있어요");
		}
		User user = users.findById(userId).orElseThrow(ApiException::unauthorized);
		if (!user.aiConsentActive()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "AI_CONSENT_REQUIRED", "AI 분석에 동의해야 쓸 수 있어요.");
		}
		if (Boolean.TRUE.equals(request.forCouple())) {
			// 커플 연결 · 동의를 확인하는 기능은 11월 3주에 만들어요. 그 전에는 커플 비교 세션을 만들 수 없어요.
			throw new ApiException(HttpStatus.FORBIDDEN, "COUPLE_ACCESS_DENIED", "커플 모드는 아직 쓸 수 없어요.");
		}

		Lookup lookup = persona.lookup(userId, request.scope(), request.anchorDate()); // 날짜 범위도 여기서 확인해요
		ChatSession session = ChatSession.start(userId, request.anchorDate(), request.scope(), request.shortcutKey(), lookup.level());

		if (lookup.level() == RecordLevel.NONE) {
			return ChatSessionResponse.from(startReady(session, null, ChatGreetings.pick(null)), null);
		}
		if (lookup.reusable() != null) {
			return ChatSessionResponse.from(
				startReady(session, lookup.reusable().analysisId(), ChatGreetings.pick(lookup.reusable().content().greetings())), null);
		}

		// 분석이 필요해요. 먼저 PREPARING으로 저장해서 돌려주고 뒤에서 분석해요.
		ChatSession saved = sessions.saveAndFlush(session);
		UUID sessionId = saved.getId();
		job.begin(sessionId);
		try {
			executor.execute(() -> job.run(sessionId, userId, request.scope(), request.anchorDate()));
		} catch (RejectedExecutionException e) {
			log.error("대화 준비를 시작하지 못했어요 sessionId={}", sessionId, e);
			saved.fail("INTERNAL_ERROR");
			sessions.save(saved);
		}
		// 줄에 넣자마자 끝날 수도 있어서(빠른 실행기) 저장된 최신 상태를 다시 읽어요
		ChatSession latest = sessions.findById(sessionId).orElse(saved);
		return ChatSessionResponse.from(latest, job.stepOf(sessionId));
	}

	/** GET /chats — "과거의 나" 목록 (최근 대화 순, 최대 50개) */
	@Transactional
	public ChatListResponse list(UUID userId) {
		List<ChatSession> all = sessions.findByUserIdAndCoupleIdIsNull(userId);
		all.forEach(this::failIfStale);
		if (all.isEmpty()) {
			return new ChatListResponse(List.of());
		}

		Set<UUID> ids = all.stream().map(ChatSession::getId).collect(Collectors.toSet());
		Map<UUID, ChatMessage> lastBySession = messages.findLastMessages(ids).stream()
			.collect(Collectors.toMap(ChatMessage::getSessionId, m -> m, (a, b) -> a));
		Set<LocalDate> dayDates = new HashSet<>();
		for (ChatSession session : all) {
			if (session.scope() == PersonaScope.DAY) {
				dayDates.add(session.getAnchorDate());
			}
		}
		Map<LocalDate, List<Emotion>> emotionsByDate = dayDates.isEmpty() ? Map.of()
			: diaries.findByUserIdAndDiaryDateIn(userId, dayDates).stream()
				.collect(Collectors.toMap(Diary::getDiaryDate, d -> d.getEmotions().stream().sorted().toList(), (a, b) -> a));

		List<ChatListItemResponse> items = all.stream()
			.map(session -> toListItem(session, lastBySession.get(session.getId()), emotionsByDate))
			.sorted(Comparator.comparing(ChatListItemResponse::lastMessageAt).reversed())
			.limit(LIST_LIMIT)
			.toList();
		return new ChatListResponse(items);
	}

	/** GET /chats/{chatId} — 세션 상태 (PREPARING이면 1~2초 간격으로 조회해요) */
	@Transactional
	public ChatSessionResponse get(UUID userId, UUID chatId) {
		ChatSession session = sessions.findByIdAndUserId(chatId, userId).orElseThrow(ApiException::notFound);
		failIfStale(session);
		return ChatSessionResponse.from(session, job.stepOf(session.getId()));
	}

	/** GET /chats/{chatId}/messages?afterSeq&limit — seq 오름차순, afterSeq보다 큰 것만 */
	@Transactional(readOnly = true)
	public MessageListResponse messages(UUID userId, UUID chatId, int afterSeq, int limit) {
		if (afterSeq < 0) {
			throw ApiException.validation("afterSeq", "0 이상이어야 해요");
		}
		if (limit < 1 || limit > 200) {
			throw ApiException.validation("limit", "1 이상 200 이하여야 해요");
		}
		sessions.findByIdAndUserId(chatId, userId).orElseThrow(ApiException::notFound);
		return new MessageListResponse(messages.findBySessionIdAndSeqGreaterThanOrderBySeqAsc(chatId, afterSeq, PageRequest.of(0, limit))
			.stream().map(ChatMessageResponse::from).toList());
	}

	/** 분석이 필요 없는 세션을 READY로 저장하고 첫 인사(seq 1)도 같이 저장해요. */
	private ChatSession startReady(ChatSession session, UUID analysisId, String greeting) {
		return tx.execute(status -> {
			session.ready(analysisId);
			ChatSession saved = sessions.saveAndFlush(session);
			messages.save(ChatMessage.create(saved.getId(), 1, ChatRole.PERSONA, greeting));
			return saved;
		});
	}

	private ChatListItemResponse toListItem(ChatSession session, ChatMessage last, Map<LocalDate, List<Emotion>> emotionsByDate) {
		OffsetDateTime lastAt = last == null ? session.getStartedAt() : last.getCreatedAt();
		return new ChatListItemResponse(
			session.getId(),
			session.scope(),
			session.getShortcutKey(),
			session.getAnchorDate(),
			session.scope() == PersonaScope.DAY ? emotionsByDate.getOrDefault(session.getAnchorDate(), List.of()) : List.of(),
			session.getRecordLevel(),
			session.getStatus(),
			last == null ? null : preview(last.getContent()),
			Kst.toKst(lastAt),
			Kst.toKst(session.getStartedAt()),
			Kst.toKst(session.getEndedAt()));
	}

	/** 마지막 메시지의 첫 줄을 PREVIEW_MAX_CHARS자까지만 보여줘요(넘으면 …). */
	static String preview(String content) {
		String firstLine = content.strip().lines().findFirst().orElse("");
		return firstLine.length() <= PREVIEW_MAX_CHARS ? firstLine : firstLine.substring(0, PREVIEW_MAX_CHARS) + "…";
	}

	/** 조회할 때 10분이 지난 PREPARING은 FAILED로 바꿔요. 앱이 영원히 기다리지 않게요. */
	private void failIfStale(ChatSession session) {
		if (session.isPreparing() && session.getStartedAt().isBefore(Kst.now().minus(STALE_AFTER))) {
			session.fail("PREPARING_TIMEOUT");
			sessions.save(session);
		}
	}
}
