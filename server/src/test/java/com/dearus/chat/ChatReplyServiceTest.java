package com.dearus.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dearus.auth.User;
import com.dearus.auth.UserRepository;
import com.dearus.common.ApiException;
import com.dearus.common.Kst;
import com.dearus.common.ai.AiClient;
import com.dearus.common.ai.AiException;
import com.dearus.common.ai.AiRequest;
import com.dearus.common.ai.AiResult;
import com.dearus.persona.PersonaAnalysisService;
import com.dearus.persona.PersonaContent;
import com.dearus.persona.PersonaScope;
import com.dearus.persona.RecordLevel;
import com.dearus.upload.SensitiveMasker;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** DB · 실제 AI 없이 대화 보내기의 규칙(검증 · 잠금 · 저장 · 실패 · 위기 신호 · 마스킹)을 확인해요. */
class ChatReplyServiceTest {

	private final ChatSessionRepository sessions = mock(ChatSessionRepository.class);
	private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
	private final UserRepository users = mock(UserRepository.class);
	private final PersonaAnalysisService persona = mock(PersonaAnalysisService.class);
	private final AiClient ai = mock(AiClient.class);
	private final UUID userId = UUID.randomUUID();
	private final UUID chatId = UUID.randomUUID();
	private final UUID analysisId = UUID.randomUUID();
	private final List<ChatMessage> store = new ArrayList<>();
	private final AtomicLong ids = new AtomicLong();
	private final PersonaContent content = new PersonaContent("짧은 반말", List.of("진짜"), "학기 중", "시험", "피곤", List.of("오늘 피곤"),
		List.of("어 왔어? ㅋㅋ"));
	private final SafetyProperties safetyProperties = new SafetyProperties(List.of(new SafetyNotice.Hotline("자살예방 상담전화", "109", "24시간")));

	private ChatSession session;
	private ChatReplyService service;
	private boolean saveFails;

	@BeforeEach
	void setUp() {
		User user = User.create("dev", "tester", "테스터");
		user.agree(false);
		when(users.findById(userId)).thenReturn(Optional.of(user));

		session = ChatSession.start(userId, Kst.today().minusDays(30), PersonaScope.AROUND, null, RecordLevel.ENOUGH);
		ReflectionTestUtils.setField(session, "id", chatId);
		session.ready(analysisId);
		when(sessions.findByIdAndUserId(any(), any())).thenAnswer(call ->
			session.getId().equals(call.getArgument(0)) && session.getUserId().equals(call.getArgument(1)) ? Optional.of(session) : Optional.empty());
		when(persona.contentOf(analysisId)).thenReturn(Optional.of(content));

		addMessage(1, ChatRole.PERSONA, "어 왔어? ㅋㅋ"); // 첫 인사
		when(messages.save(any())).thenAnswer(call -> {
			if (saveFails) {
				throw new DataIntegrityViolationException("uq_chat_messages_seq");
			}
			ChatMessage message = call.getArgument(0);
			ReflectionTestUtils.setField(message, "id", ids.incrementAndGet());
			store.add(message);
			return message;
		});
		when(messages.findBySessionIdOrderBySeqDesc(any(), any(Pageable.class))).thenAnswer(call -> store.stream()
			.sorted(Comparator.comparingInt(ChatMessage::getSeq).reversed()).limit(((Pageable) call.getArgument(1)).getPageSize()).toList());
		when(messages.findFirstBySessionIdOrderBySeqAsc(any())).thenAnswer(call -> store.stream().min(Comparator.comparingInt(ChatMessage::getSeq)));

		when(ai.stream(any(), any())).thenAnswer(call -> {
			Consumer<String> onText = call.getArgument(1);
			onText.accept("어, ");
			onText.accept("그래?");
			return new AiResult("어, 그래?", 12, 3);
		});
		service = new ChatReplyService(sessions, messages, users, persona, new ChatPromptBuilder(new SensitiveMasker()), new RuleSafetyDetector(),
			safetyProperties, ai, new TransactionTemplate(mock(PlatformTransactionManager.class)));
	}

	private void addMessage(int seq, ChatRole role, String text) {
		ChatMessage message = ChatMessage.create(chatId, seq, role, text);
		ReflectionTestUtils.setField(message, "id", ids.incrementAndGet());
		store.add(message);
	}

	/** 받은 글자 조각과 위기 신호를 순서대로 모아요. */
	private static class Recorder implements ChatReplyService.Sink {

		final List<String> events = new ArrayList<>();

		@Override
		public void safety(SafetyNotice notice) {
			events.add("safety:" + notice.reason());
		}

		@Override
		public void token(String text) {
			events.add("token:" + text);
		}
	}

	private ApiException apiError(Runnable action) {
		try {
			action.run();
		} catch (ApiException e) {
			return e;
		}
		throw new AssertionError("ApiException이 나야 하는데 나지 않았어요");
	}

	// ───────── 준비(검증) ─────────

	@Test
	void 남의_세션이나_없는_세션은_404예요() {
		assertThat(apiError(() -> service.prepare(UUID.randomUUID(), chatId, "안녕")).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(apiError(() -> service.prepare(userId, UUID.randomUUID(), "안녕")).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void AI_분석_동의를_철회했으면_403이에요() {
		User revoked = User.create("dev", "x", "x");
		revoked.agree(false);
		revoked.revokeAiConsent();
		when(users.findById(userId)).thenReturn(Optional.of(revoked));

		ApiException e = apiError(() -> service.prepare(userId, chatId, "안녕"));

		assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(e.getCode()).isEqualTo("AI_CONSENT_REQUIRED");
		verify(ai, never()).stream(any(), any());
	}

	@Test
	void 끝난_세션은_409_CHAT_ENDED예요() {
		ReflectionTestUtils.setField(session, "endedAt", Kst.now());

		ApiException e = apiError(() -> service.prepare(userId, chatId, "안녕"));

		assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(e.getCode()).isEqualTo("CHAT_ENDED");
	}

	@Test
	void 준비_중이거나_실패한_세션은_409_CHAT_NOT_READY예요() {
		ReflectionTestUtils.setField(session, "status", ChatStatus.PREPARING);
		assertThat(apiError(() -> service.prepare(userId, chatId, "안녕")).getCode()).isEqualTo("CHAT_NOT_READY");

		session.fail("AI_UNAVAILABLE");
		assertThat(apiError(() -> service.prepare(userId, chatId, "안녕")).getCode()).isEqualTo("CHAT_NOT_READY");
	}

	@Test
	void 앞_답변이_끝나기_전에_또_보내면_409_CHAT_BUSY예요() {
		service.prepare(userId, chatId, "첫 번째");

		ApiException e = apiError(() -> service.prepare(userId, chatId, "두 번째"));

		assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(e.getCode()).isEqualTo("CHAT_BUSY");
	}

	@Test
	void 답변이_끝나면_잠금이_풀려서_다시_보낼_수_있어요() {
		service.run(service.prepare(userId, chatId, "첫 번째"), ChatReplyService.Sink.NONE);

		assertThat(service.prepare(userId, chatId, "두 번째").nextSeq()).isEqualTo(4);
	}

	@Test
	void 답변이_실패해도_잠금이_풀려요() {
		doThrow(new AiException(AiException.Reason.UNAVAILABLE, "닿지 못했어요")).when(ai).stream(any(), any());
		ChatReplyService.Prepared prepared = service.prepare(userId, chatId, "안녕");

		assertThatThrownBy(() -> service.run(prepared, ChatReplyService.Sink.NONE)).isInstanceOf(AiException.class);

		assertThat(service.prepare(userId, chatId, "다시").nextSeq()).isEqualTo(2);
	}

	@Test
	void 실행을_못_하면_release로_잠금을_풀어요() {
		ChatReplyService.Prepared prepared = service.prepare(userId, chatId, "안녕");
		service.release(prepared);

		assertThat(service.prepare(userId, chatId, "다시")).isNotNull();
	}

	@Test
	void 준비_중_오류가_나도_잠금이_남지_않아요() {
		doThrow(new IllegalStateException("복호화에 실패했어요")).when(persona).contentOf(analysisId);

		assertThatThrownBy(() -> service.prepare(userId, chatId, "안녕")).isInstanceOf(IllegalStateException.class);

		doReturn(Optional.of(content)).when(persona).contentOf(analysisId);
		assertThat(service.prepare(userId, chatId, "안녕")).isNotNull();
	}

	@Test
	void 준비하면_분석_결과와_첫_인사로_프롬프트를_만들고_다음_seq를_정해요() {
		addMessage(2, ChatRole.USER, "응 나 미래의 너야");
		addMessage(3, ChatRole.PERSONA, "헐 진짜?");

		ChatReplyService.Prepared prepared = service.prepare(userId, chatId, "시험 잘 봤어");

		AiRequest request = prepared.request();
		assertThat(prepared.nextSeq()).isEqualTo(4);
		assertThat(request.system()).contains("네 말투: 짧은 반말").contains("대화는 네가 먼저 \"어 왔어? ㅋㅋ\"라고 인사하면서 시작됐어.");
		// 첫 인사(seq 1)는 시스템 프롬프트에만 있고 메시지는 user로 시작해요
		assertThat(request.messages()).extracting(m -> m.text()).containsExactly("응 나 미래의 너야", "헐 진짜?", "시험 잘 봤어");
		assertThat(prepared.safety()).isNull();
	}

	@Test
	void 분석이_없는_세션은_기본_말투로_준비해요() {
		ReflectionTestUtils.setField(session, "analysisId", null);

		AiRequest request = service.prepare(userId, chatId, "안녕").request();

		assertThat(request.system()).contains("남은 기록이 없어서");
		verify(persona, never()).contentOf(any());
	}

	// ───────── 실행(AI · 저장) ─────────

	@Test
	void 글자_조각을_순서대로_흘려보내고_내_말과_답변을_이어서_저장해요() {
		Recorder recorder = new Recorder();

		ChatReplyResponse reply = service.run(service.prepare(userId, chatId, "안녕"), recorder);

		assertThat(recorder.events).containsExactly("token:어, ", "token:그래?");
		assertThat(reply.userMessage().seq()).isEqualTo(2);
		assertThat(reply.userMessage().role()).isEqualTo(ChatRole.USER);
		assertThat(reply.userMessage().content()).isEqualTo("안녕");
		assertThat(reply.personaMessage().seq()).isEqualTo(3);
		assertThat(reply.personaMessage().role()).isEqualTo(ChatRole.PERSONA);
		assertThat(reply.personaMessage().content()).isEqualTo("어, 그래?");
		assertThat(reply.safety()).isNull();
		assertThat(store).hasSize(3); // 첫 인사 + 내 말 + 답변
	}

	@Test
	void 내_말은_받은_시각으로_저장해요() {
		ChatReplyService.Prepared prepared = service.prepare(userId, chatId, "안녕");

		service.run(prepared, ChatReplyService.Sink.NONE);

		ChatMessage userMessage = store.stream().filter(m -> m.getRole() == ChatRole.USER).findFirst().orElseThrow();
		assertThat(userMessage.getCreatedAt()).isEqualTo(prepared.receivedAt());
	}

	@Test
	void AI에는_가린_글자를_보내고_저장은_원문으로_해요() {
		ChatReplyService.Prepared prepared = service.prepare(userId, chatId, "내 번호는 010-1234-5678이야");
		ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);

		service.run(prepared, ChatReplyService.Sink.NONE);

		verify(ai).stream(captor.capture(), any());
		assertThat(captor.getValue().messages().get(captor.getValue().messages().size() - 1).text()).isEqualTo("내 번호는 [전화번호]이야");
		assertThat(store.stream().filter(m -> m.getRole() == ChatRole.USER).findFirst().orElseThrow().getContent()).isEqualTo("내 번호는 010-1234-5678이야");
	}

	@Test
	void 위기_신호가_있으면_글자보다_먼저_안내를_보내고_응답에도_담아요() {
		Recorder recorder = new Recorder();

		ChatReplyResponse reply = service.run(service.prepare(userId, chatId, "요즘 너무 힘들어서 죽고 싶어"), recorder);

		assertThat(recorder.events).containsExactly("safety:DISTRESS", "token:어, ", "token:그래?");
		assertThat(reply.safety()).isNotNull();
		assertThat(reply.safety().hotlines()).extracting(SafetyNotice.Hotline::phone).containsExactly("109");
	}

	@Test
	void 위기_신호가_있으면_프롬프트에_걱정하라는_지침이_더해져요() {
		AiRequest request = service.prepare(userId, chatId, "그냥 사라지고 싶어").request();

		assertThat(request.system()).contains("가볍게 넘기지 말고 진지하게 걱정");
	}

	@Test
	void AI가_실패하면_아무것도_저장하지_않아요() {
		doThrow(new AiException(AiException.Reason.UNAVAILABLE, "닿지 못했어요")).when(ai).stream(any(), any());
		ChatReplyService.Prepared prepared = service.prepare(userId, chatId, "안녕");

		assertThatThrownBy(() -> service.run(prepared, ChatReplyService.Sink.NONE))
			.isInstanceOfSatisfying(AiException.class, e -> assertThat(e.reason()).isEqualTo(AiException.Reason.UNAVAILABLE));

		assertThat(store).hasSize(1); // 첫 인사뿐
	}

	@Test
	void 도중에_글자를_받은_뒤_실패해도_저장하지_않아요() {
		doAnswer(call -> {
			Consumer<String> onText = call.getArgument(1);
			onText.accept("어, ");
			throw new AiException(AiException.Reason.UNAVAILABLE, "중간에 끊겼어요");
		}).when(ai).stream(any(), any());
		Recorder recorder = new Recorder();
		ChatReplyService.Prepared prepared = service.prepare(userId, chatId, "안녕");

		assertThatThrownBy(() -> service.run(prepared, recorder)).isInstanceOf(AiException.class);

		assertThat(recorder.events).containsExactly("token:어, ");
		assertThat(store).hasSize(1);
	}

	@Test
	void 답이_비어_있으면_BAD_RESPONSE이고_저장하지_않아요() {
		doReturn(new AiResult("   ", 1, 0)).when(ai).stream(any(), any());
		ChatReplyService.Prepared prepared = service.prepare(userId, chatId, "안녕");

		assertThatThrownBy(() -> service.run(prepared, ChatReplyService.Sink.NONE))
			.isInstanceOfSatisfying(AiException.class, e -> assertThat(e.reason()).isEqualTo(AiException.Reason.BAD_RESPONSE));
		assertThat(store).hasSize(1);
	}

	@Test
	void 거절도_지금은_AI_REFUSED_오류예요() {
		doThrow(new AiException(AiException.Reason.REFUSED, "거절")).when(ai).stream(any(), any());
		ChatReplyService.Prepared prepared = service.prepare(userId, chatId, "안녕");

		assertThatThrownBy(() -> service.run(prepared, ChatReplyService.Sink.NONE))
			.isInstanceOfSatisfying(AiException.class, e -> assertThat(e.reason().code()).isEqualTo("AI_REFUSED"));
		assertThat(store).hasSize(1);
	}

	@Test
	void 같은_seq가_이미_저장돼_있으면_CHAT_BUSY예요() {
		ChatReplyService.Prepared prepared = service.prepare(userId, chatId, "안녕");
		saveFails = true; // 다른 서버가 먼저 같은 seq를 저장했다고 가정해요

		ApiException e = apiError(() -> service.run(prepared, ChatReplyService.Sink.NONE));

		assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(e.getCode()).isEqualTo("CHAT_BUSY");
	}

	@Test
	void 이력은_첫_인사를_빼고_최근_것부터_자르지만_다음_seq는_맨_뒤_기준이에요() {
		for (int seq = 2; seq <= 91; seq++) {
			addMessage(seq, seq % 2 == 0 ? ChatRole.USER : ChatRole.PERSONA, "말 " + seq);
		}

		ChatReplyService.Prepared prepared = service.prepare(userId, chatId, "새 말");

		assertThat(prepared.nextSeq()).isEqualTo(92);
		assertThat(prepared.request().messages().size()).isLessThanOrEqualTo(ChatPromptBuilder.MAX_HISTORY_MESSAGES + 1);
		assertThat(prepared.request().messages().get(0).role()).isEqualTo(com.dearus.common.ai.AiMessage.Role.USER);
	}

	// ───────── 오류 변환 · SSE 보내기 ─────────

	@Test
	void AI_오류는_JSON_응답일_때_503이에요() {
		for (AiException.Reason reason : AiException.Reason.values()) {
			ApiException e = ChatReplyService.toApiException(new AiException(reason, "x"));

			assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
			assertThat(e.getCode()).isEqualTo(reason.code());
		}
	}

	@Test
	void 클라이언트가_끊겨_보내기가_실패해도_예외를_던지지_않아요() {
		int[] attempts = {0};
		SseEmitter broken = new SseEmitter() {
			@Override
			public synchronized void send(SseEventBuilder builder) throws IOException {
				attempts[0]++;
				throw new IOException("끊겼어요");
			}
		};
		SseSink sink = new SseSink(broken);

		sink.token("어, ");
		sink.token("그래?");
		sink.safety(new SafetyNotice("DISTRESS", List.of()));
		sink.send("done", "x");
		sink.complete();

		assertThat(attempts[0]).isEqualTo(1); // 한 번 실패한 뒤에는 더 시도하지 않아요
	}

	@Test
	void Accept가_application_json뿐일_때만_JSON_응답이에요() {
		assertThat(ChatController.wantsJson("application/json")).isTrue();
		assertThat(ChatController.wantsJson("application/json;charset=UTF-8")).isTrue();
		assertThat(ChatController.wantsJson(null)).isFalse();
		assertThat(ChatController.wantsJson("text/event-stream")).isFalse();
		assertThat(ChatController.wantsJson("*/*")).isFalse();
		assertThat(ChatController.wantsJson("application/json, text/event-stream")).isFalse();
		assertThat(ChatController.wantsJson("application/json, */*")).isFalse();
	}
}
