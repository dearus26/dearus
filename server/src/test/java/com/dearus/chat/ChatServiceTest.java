package com.dearus.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dearus.auth.User;
import com.dearus.auth.UserRepository;
import com.dearus.common.ApiException;
import com.dearus.common.Kst;
import com.dearus.common.ai.AiException;
import com.dearus.diary.Diary;
import com.dearus.diary.DiaryRepository;
import com.dearus.diary.Emotion;
import com.dearus.persona.PersonaAnalysisService;
import com.dearus.persona.PersonaAnalysisService.Lookup;
import com.dearus.persona.PersonaAnalysisService.Result;
import com.dearus.persona.PersonaAnalysisService.Step;
import com.dearus.persona.PersonaContent;
import com.dearus.persona.PersonaScope;
import com.dearus.persona.RecordLevel;
import com.dearus.persona.ShortcutKey;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** DB · 실제 AI 없이 대화 세션의 규칙(바로 READY · PREPARING에서 READY/FAILED · 목록 · 메시지)을 확인해요. */
class ChatServiceTest {

	private final UserRepository users = mock(UserRepository.class);
	private final ChatSessionRepository sessions = mock(ChatSessionRepository.class);
	private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
	private final DiaryRepository diaries = mock(DiaryRepository.class);
	private final PersonaAnalysisService persona = mock(PersonaAnalysisService.class);
	private final UUID userId = UUID.randomUUID();
	private final LocalDate anchor = Kst.today().minusDays(30);
	private final List<ChatSession> sessionStore = new ArrayList<>();
	private final List<ChatMessage> messageStore = new ArrayList<>();
	private final AtomicLong messageIds = new AtomicLong();
	private final List<Runnable> queued = new ArrayList<>();
	private final PersonaContent content = new PersonaContent("짧은 반말", List.of("ㅋㅋ"), "학기 중", "시험", "피곤",
		List.of("오늘 피곤"), List.of("어 왔어? ㅋㅋ", "오랜만이네", "야 뭐해"));

	private ChatPreparationJob job;
	private ChatService service;
	private Executor executor;

	@BeforeEach
	void setUp() {
		User user = User.create("dev", "tester", "테스터");
		user.agree(false);
		when(users.findById(userId)).thenReturn(Optional.of(user));

		when(sessions.saveAndFlush(any())).thenAnswer(call -> store(call.getArgument(0)));
		when(sessions.save(any())).thenAnswer(call -> store(call.getArgument(0)));
		when(sessions.findById(any())).thenAnswer(call -> sessionStore.stream().filter(s -> s.getId().equals(call.getArgument(0))).findFirst());
		when(sessions.findByIdAndUserId(any(), any())).thenAnswer(call -> sessionStore.stream()
			.filter(s -> s.getId().equals(call.getArgument(0)) && s.getUserId().equals(call.getArgument(1))).findFirst());
		when(sessions.findByUserIdAndCoupleIdIsNull(any())).thenAnswer(call -> sessionStore.stream()
			.filter(s -> s.getUserId().equals(call.getArgument(0)) && s.getCoupleId() == null).toList());

		when(messages.save(any())).thenAnswer(call -> {
			ChatMessage message = call.getArgument(0);
			ReflectionTestUtils.setField(message, "id", messageIds.incrementAndGet());
			messageStore.add(message);
			return message;
		});
		when(messages.findBySessionIdAndSeqGreaterThanOrderBySeqAsc(any(), org.mockito.ArgumentMatchers.anyInt(), any(Pageable.class)))
			.thenAnswer(call -> messageStore.stream()
				.filter(m -> m.getSessionId().equals(call.getArgument(0)) && m.getSeq() > (int) call.getArgument(1))
				.sorted(Comparator.comparingInt(ChatMessage::getSeq))
				.limit(((Pageable) call.getArgument(2)).getPageSize()).toList());
		when(messages.findLastMessages(anyCollection())).thenAnswer(call -> {
			java.util.Collection<UUID> ids = call.getArgument(0);
			return ids.stream().map(id -> messageStore.stream().filter(m -> m.getSessionId().equals(id)).max(Comparator.comparing(ChatMessage::getId)))
				.flatMap(Optional::stream).toList();
		});

		executor = queued::add;
		TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
		job = new ChatPreparationJob(persona, sessions, messages, tx);
		service = new ChatService(users, sessions, messages, diaries, persona, job, tx, command -> executor.execute(command));
	}

	private ChatSession store(ChatSession session) {
		if (session.getId() == null) {
			ReflectionTestUtils.setField(session, "id", UUID.randomUUID()); // DB에 저장되면 생기는 ID
		}
		if (!sessionStore.contains(session)) {
			sessionStore.add(session);
		}
		return session;
	}

	private ChatCreateRequest around() {
		return new ChatCreateRequest(anchor, PersonaScope.AROUND, ShortcutKey.ONE_MONTH, false);
	}

	private ChatCreateRequest day() {
		return new ChatCreateRequest(anchor, PersonaScope.DAY, null, false);
	}

	private ApiException apiError(Runnable action) {
		try {
			action.run();
		} catch (ApiException e) {
			return e;
		}
		throw new AssertionError("ApiException이 나야 하는데 나지 않았어요");
	}

	private void runQueued() {
		new ArrayList<>(queued).forEach(Runnable::run);
		queued.clear();
	}

	// ───────── 만들기 ─────────

	@Test
	void 바로가기_키는_전후_2주일_때만_보낼_수_있어요() {
		ApiException e = apiError(() -> service.create(userId, new ChatCreateRequest(anchor, PersonaScope.DAY, ShortcutKey.ONE_MONTH, false)));

		assertThat(e.getCode()).isEqualTo("VALIDATION_FAILED");
		verify(persona, never()).lookup(any(), any(), any());
	}

	@Test
	void AI_분석_동의가_없으면_403이에요() {
		User revoked = User.create("dev", "x", "x");
		revoked.agree(false);
		revoked.revokeAiConsent();
		when(users.findById(userId)).thenReturn(Optional.of(revoked));

		ApiException e = apiError(() -> service.create(userId, around()));

		assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(e.getCode()).isEqualTo("AI_CONSENT_REQUIRED");
		assertThat(sessionStore).isEmpty();
	}

	@Test
	void 커플_세션은_커플_기능을_만들기_전까지_403이에요() {
		ApiException e = apiError(() -> service.create(userId, new ChatCreateRequest(anchor, PersonaScope.DAY, null, true)));

		assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(e.getCode()).isEqualTo("COUPLE_ACCESS_DENIED");
		verify(persona, never()).lookup(any(), any(), any());
	}

	@Test
	void 날짜가_범위를_벗어나면_400이고_세션을_만들지_않아요() {
		when(persona.lookup(any(), any(), any())).thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "ANCHOR_OUT_OF_RANGE", "고를 수 없는 날짜예요."));

		ApiException e = apiError(() -> service.create(userId, around()));

		assertThat(e.getCode()).isEqualTo("ANCHOR_OUT_OF_RANGE");
		assertThat(sessionStore).isEmpty();
	}

	@Test
	void 기록이_없으면_분석_없이_바로_READY이고_기본_인사가_첫_메시지예요() {
		when(persona.lookup(any(), any(), any())).thenReturn(new Lookup(RecordLevel.NONE, null));

		ChatSessionResponse response = service.create(userId, day());

		assertThat(response.status()).isEqualTo(ChatStatus.READY);
		assertThat(response.level()).isEqualTo(RecordLevel.NONE);
		assertThat(response.preparingStep()).isNull();
		assertThat(response.mode()).isEqualTo(ChatMode.PERSONAL);
		assertThat(sessionStore.get(0).getAnalysisId()).isNull();
		assertThat(messageStore).hasSize(1);
		assertThat(messageStore.get(0).getSeq()).isEqualTo(1);
		assertThat(messageStore.get(0).getRole()).isEqualTo(ChatRole.PERSONA);
		assertThat(ChatGreetings.DEFAULT).contains(messageStore.get(0).getContent());
		assertThat(queued).isEmpty();
		verify(persona, never()).getOrCreate(any(), any(), any(), any());
	}

	@Test
	void 저장된_분석을_재사용할_수_있으면_바로_READY이고_분석의_인사가_첫_메시지예요() {
		UUID analysisId = UUID.randomUUID();
		when(persona.lookup(any(), any(), any())).thenReturn(new Lookup(RecordLevel.ENOUGH, new Result(content, analysisId, true)));

		ChatSessionResponse response = service.create(userId, around());

		assertThat(response.status()).isEqualTo(ChatStatus.READY);
		assertThat(response.level()).isEqualTo(RecordLevel.ENOUGH);
		assertThat(response.shortcutKey()).isEqualTo(ShortcutKey.ONE_MONTH);
		assertThat(sessionStore.get(0).getAnalysisId()).isEqualTo(analysisId);
		assertThat(content.greetings()).contains(messageStore.get(0).getContent());
		assertThat(queued).isEmpty();
	}

	@Test
	void 응답의_구간은_세션을_만든_날을_기준으로_계산해요() {
		when(persona.lookup(any(), any(), any())).thenReturn(new Lookup(RecordLevel.NONE, null));

		ChatSessionResponse around = service.create(userId, around());
		ChatSessionResponse day = service.create(userId, day());

		assertThat(around.windowStart()).isEqualTo(anchor.minusDays(14));
		assertThat(around.windowEnd()).isEqualTo(anchor.plusDays(14));
		assertThat(day.windowStart()).isEqualTo(anchor);
		assertThat(day.windowEnd()).isEqualTo(anchor);
	}

	@Test
	void 분석이_필요하면_PREPARING으로_돌려주고_뒤에서_분석해서_READY로_바꿔요() {
		when(persona.lookup(any(), any(), any())).thenReturn(new Lookup(RecordLevel.ENOUGH, null));
		UUID analysisId = UUID.randomUUID();
		when(persona.getOrCreate(any(), any(), any(), any())).thenAnswer(call -> {
			Consumer<Step> progress = call.getArgument(3);
			progress.accept(Step.COLLECT);
			progress.accept(Step.ANALYZE);
			return Optional.of(new Result(content, analysisId, false));
		});

		ChatSessionResponse created = service.create(userId, around());

		assertThat(created.status()).isEqualTo(ChatStatus.PREPARING);
		assertThat(created.preparingStep()).isEqualTo(Step.COLLECT);
		assertThat(messageStore).isEmpty();
		assertThat(queued).hasSize(1);
		assertThat(service.get(userId, created.id()).status()).isEqualTo(ChatStatus.PREPARING);

		runQueued();

		ChatSessionResponse ready = service.get(userId, created.id());
		assertThat(ready.status()).isEqualTo(ChatStatus.READY);
		assertThat(ready.preparingStep()).isNull();
		assertThat(job.stepOf(created.id())).isNull();
		assertThat(sessionStore.get(0).getAnalysisId()).isEqualTo(analysisId);
		assertThat(messageStore).hasSize(1);
		assertThat(content.greetings()).contains(messageStore.get(0).getContent());
	}

	@Test
	void 분석_단계는_조회로_알_수_있어요() {
		when(persona.lookup(any(), any(), any())).thenReturn(new Lookup(RecordLevel.ENOUGH, null));
		ChatSessionResponse created = service.create(userId, around());

		// 작업이 도는 중이라고 가정하고 단계를 바꿔 봐요
		job.begin(created.id());
		assertThat(service.get(userId, created.id()).preparingStep()).isEqualTo(Step.COLLECT);
	}

	@Test
	void 분석이_AI_오류로_실패하면_FAILED와_이유가_남고_메시지는_없어요() {
		when(persona.lookup(any(), any(), any())).thenReturn(new Lookup(RecordLevel.ENOUGH, null));
		when(persona.getOrCreate(any(), any(), any(), any())).thenThrow(new AiException(AiException.Reason.UNAVAILABLE, "닿지 못했어요"));
		ChatSessionResponse created = service.create(userId, around());

		runQueued();

		ChatSessionResponse failed = service.get(userId, created.id());
		assertThat(failed.status()).isEqualTo(ChatStatus.FAILED);
		assertThat(failed.failureCode()).isEqualTo("AI_UNAVAILABLE");
		assertThat(messageStore).isEmpty();
	}

	@Test
	void 예상하지_못한_오류는_INTERNAL_ERROR로_실패해요() {
		when(persona.lookup(any(), any(), any())).thenReturn(new Lookup(RecordLevel.ENOUGH, null));
		when(persona.getOrCreate(any(), any(), any(), any())).thenThrow(new IllegalStateException("복호화에 실패했어요"));
		ChatSessionResponse created = service.create(userId, around());

		runQueued();

		assertThat(service.get(userId, created.id()).failureCode()).isEqualTo("INTERNAL_ERROR");
	}

	@Test
	void 작업_줄에_넣지_못하면_바로_FAILED예요() {
		when(persona.lookup(any(), any(), any())).thenReturn(new Lookup(RecordLevel.ENOUGH, null));
		executor = command -> {
			throw new RejectedExecutionException("종료 중");
		};

		ChatSessionResponse response = service.create(userId, around());

		assertThat(response.status()).isEqualTo(ChatStatus.FAILED);
		assertThat(response.failureCode()).isEqualTo("INTERNAL_ERROR");
	}

	@Test
	void 분석하는_동안_세션이_지워졌으면_아무것도_저장하지_않아요() {
		when(persona.lookup(any(), any(), any())).thenReturn(new Lookup(RecordLevel.ENOUGH, null));
		when(persona.getOrCreate(any(), any(), any(), any())).thenAnswer(call -> {
			sessionStore.clear(); // 탈퇴 등으로 세션이 사라졌어요
			return Optional.of(new Result(content, UUID.randomUUID(), false));
		});
		service.create(userId, around());

		runQueued();

		assertThat(messageStore).isEmpty();
	}

	@Test
	void 분석하는_동안_기록이_모두_지워져_결과가_없으면_기본_말투로_READY예요() {
		when(persona.lookup(any(), any(), any())).thenReturn(new Lookup(RecordLevel.ENOUGH, null));
		when(persona.getOrCreate(any(), any(), any(), any())).thenReturn(Optional.empty());
		ChatSessionResponse created = service.create(userId, around());

		runQueued();

		assertThat(service.get(userId, created.id()).status()).isEqualTo(ChatStatus.READY);
		assertThat(sessionStore.get(0).getAnalysisId()).isNull();
		assertThat(ChatGreetings.DEFAULT).contains(messageStore.get(0).getContent());
	}

	// ───────── 조회 ─────────

	@Test
	void 남의_세션은_404예요() {
		when(persona.lookup(any(), any(), any())).thenReturn(new Lookup(RecordLevel.NONE, null));
		ChatSessionResponse created = service.create(userId, day());
		UUID other = UUID.randomUUID();

		assertThat(apiError(() -> service.get(other, created.id())).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(apiError(() -> service.messages(other, created.id(), 0, 50)).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(apiError(() -> service.get(userId, UUID.randomUUID())).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void PREPARING이_10분_넘게_끝나지_않으면_조회할_때_FAILED로_바꿔요() {
		when(persona.lookup(any(), any(), any())).thenReturn(new Lookup(RecordLevel.ENOUGH, null));
		ChatSessionResponse created = service.create(userId, around());
		ReflectionTestUtils.setField(sessionStore.get(0), "startedAt", Kst.now().minusMinutes(11));

		ChatSessionResponse response = service.get(userId, created.id());

		assertThat(response.status()).isEqualTo(ChatStatus.FAILED);
		assertThat(response.failureCode()).isEqualTo("PREPARING_TIMEOUT");
	}

	@Test
	void 목록은_최근_대화_순이고_미리보기와_하루_감정을_보여주고_커플_세션은_빠져요() {
		ChatSession older = addSession(PersonaScope.DAY, anchor, Kst.now().minusDays(3));
		ChatSession newer = addSession(PersonaScope.AROUND, anchor.minusDays(60), Kst.now().minusDays(2));
		ChatSession noMessage = addSession(PersonaScope.DAY, anchor.minusDays(5), Kst.now().minusHours(1));
		ChatSession couple = addSession(PersonaScope.DAY, anchor, Kst.now());
		ReflectionTestUtils.setField(couple, "coupleId", UUID.randomUUID());
		addMessage(older, 1, Kst.now().minusDays(3));
		addMessage(older, 2, Kst.now().minusHours(5)); // older의 마지막 메시지가 가장 최근이에요
		addMessage(newer, 1, Kst.now().minusDays(2));
		ReflectionTestUtils.setField(messageStore.get(1), "content", "첫 줄이에요\n둘째 줄");
		when(diaries.findByUserIdAndDiaryDateIn(any(), anyCollection())).thenAnswer(call ->
			List.of(Diary.create(userId, anchor, "일기", Set.of(Emotion.ANGRY, Emotion.TIRED))));

		ChatListResponse response = service.list(userId);

		// 마지막 대화 시각 순: noMessage(1시간 전에 만듦) > older(마지막 메시지 5시간 전) > newer(2일 전)
		assertThat(response.items()).extracting(ChatListItemResponse::id).containsExactly(noMessage.getId(), older.getId(), newer.getId());
		ChatListItemResponse empty = response.items().get(0);
		assertThat(empty.lastMessagePreview()).isNull(); // 대화가 없으면 미리보기 없음
		assertThat(empty.lastMessageAt()).isEqualTo(Kst.toKst(noMessage.getStartedAt())); // 만든 시각
		ChatListItemResponse talked = response.items().get(1);
		assertThat(talked.lastMessagePreview()).isEqualTo("첫 줄이에요");
		assertThat(talked.emotions()).containsExactly(Emotion.TIRED, Emotion.ANGRY);
		assertThat(response.items().get(2).emotions()).isEmpty(); // 전후 2주는 감정 없음
	}

	@Test
	void 목록은_최대_50개예요() {
		for (int i = 0; i < 55; i++) {
			addSession(PersonaScope.AROUND, anchor.minusDays(i), Kst.now().minusMinutes(i));
		}

		assertThat(service.list(userId).items()).hasSize(50);
	}

	@Test
	void 대화가_없으면_빈_목록이에요() {
		assertThat(service.list(userId).items()).isEmpty();
	}

	@Test
	void 메시지는_afterSeq보다_큰_것만_seq_순으로_limit개까지_줘요() {
		when(persona.lookup(any(), any(), any())).thenReturn(new Lookup(RecordLevel.NONE, null));
		ChatSessionResponse created = service.create(userId, day());
		ChatSession session = sessionStore.get(0);
		addMessage(session, 2, Kst.now());
		addMessage(session, 3, Kst.now());

		assertThat(service.messages(userId, created.id(), 0, 50).items()).extracting(ChatMessageResponse::seq).containsExactly(1, 2, 3);
		assertThat(service.messages(userId, created.id(), 1, 50).items()).extracting(ChatMessageResponse::seq).containsExactly(2, 3);
		assertThat(service.messages(userId, created.id(), 0, 2).items()).extracting(ChatMessageResponse::seq).containsExactly(1, 2);
		assertThat(service.messages(userId, created.id(), 3, 50).items()).isEmpty();
		assertThat(service.messages(userId, created.id(), 0, 50).items().get(0).role()).isEqualTo(ChatRole.PERSONA);
	}

	@Test
	void PREPARING인_세션의_메시지는_비어_있어요() {
		when(persona.lookup(any(), any(), any())).thenReturn(new Lookup(RecordLevel.ENOUGH, null));
		ChatSessionResponse created = service.create(userId, around());

		assertThat(service.messages(userId, created.id(), 0, 50).items()).isEmpty();
	}

	@Test
	void 메시지_조회의_afterSeq와_limit_범위를_확인해요() {
		when(persona.lookup(any(), any(), any())).thenReturn(new Lookup(RecordLevel.NONE, null));
		ChatSessionResponse created = service.create(userId, day());

		assertThatThrownBy(() -> service.messages(userId, created.id(), -1, 50)).isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> service.messages(userId, created.id(), 0, 0)).isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> service.messages(userId, created.id(), 0, 201)).isInstanceOf(ApiException.class);
		assertThat(service.messages(userId, created.id(), 0, 200).items()).hasSize(1);
	}

	@Test
	void 미리보기는_첫_줄을_60자까지만_보여줘요() {
		assertThat(ChatService.preview("  안녕\n둘째 줄  ")).isEqualTo("안녕");
		assertThat(ChatService.preview("가".repeat(60))).isEqualTo("가".repeat(60));
		assertThat(ChatService.preview("가".repeat(61))).isEqualTo("가".repeat(60) + "…");
	}

	private ChatSession addSession(PersonaScope scope, LocalDate sessionAnchor, java.time.OffsetDateTime startedAt) {
		ChatSession session = ChatSession.start(userId, sessionAnchor, scope, null, RecordLevel.ENOUGH);
		ReflectionTestUtils.setField(session, "startedAt", startedAt);
		session.ready(null);
		return store(session);
	}

	private void addMessage(ChatSession session, int seq, java.time.OffsetDateTime createdAt) {
		ChatMessage message = ChatMessage.create(session.getId(), seq, ChatRole.PERSONA, "메시지 " + seq);
		ReflectionTestUtils.setField(message, "createdAt", createdAt);
		ReflectionTestUtils.setField(message, "id", messageIds.incrementAndGet());
		messageStore.add(message);
	}
}
