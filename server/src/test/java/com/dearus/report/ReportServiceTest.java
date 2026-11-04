package com.dearus.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dearus.auth.User;
import com.dearus.auth.UserRepository;
import com.dearus.chat.ChatMessage;
import com.dearus.chat.ChatMessageRepository;
import com.dearus.chat.ChatMode;
import com.dearus.chat.ChatReplyService;
import com.dearus.chat.ChatRole;
import com.dearus.chat.ChatSession;
import com.dearus.chat.ChatSessionRepository;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** DB · 실제 AI 없이 대화 끝내기와 리포트의 규칙(검증 · 잠금 · 상태 전환 · 재시도 · 마스킹)을 확인해요. */
class ReportServiceTest {

	private static final String GOOD_JSON = "{\"pastConcern\": \"그때 고민\", \"presentChange\": \"지금 변화\", \"oneLineSummary\": \"한 줄 요약\"}";

	private final ChatSessionRepository sessions = mock(ChatSessionRepository.class);
	private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
	private final UserRepository users = mock(UserRepository.class);
	private final ReportRepository reports = mock(ReportRepository.class);
	private final PersonaAnalysisService personas = mock(PersonaAnalysisService.class);
	private final ChatReplyService replies = mock(ChatReplyService.class);
	private final AiClient ai = mock(AiClient.class);
	private final UUID userId = UUID.randomUUID();
	private final UUID chatId = UUID.randomUUID();
	private final UUID analysisId = UUID.randomUUID();
	private final List<ChatMessage> store = new ArrayList<>();
	private final List<Runnable> queued = new ArrayList<>();
	private final PersonaContent persona = new PersonaContent("짧은 반말", List.of("진짜"), "학기 중", "진로 고민", "피곤", List.of("오늘 피곤"),
		List.of("어 왔어?"));
	private final OffsetDateTime start = Kst.now().minusHours(1);

	private User user;
	private ChatSession session;
	private Report report; // reports 테이블의 한 행(없으면 null)
	private boolean rejectExecutor;
	private ReportService service;

	@BeforeEach
	void setUp() {
		user = User.create("dev", "tester", "테스터");
		user.agree(false);
		when(users.findById(userId)).thenReturn(Optional.of(user));

		session = ChatSession.start(userId, Kst.today().minusDays(30), PersonaScope.AROUND, null, RecordLevel.ENOUGH);
		ReflectionTestUtils.setField(session, "id", chatId);
		session.ready(analysisId);
		when(sessions.findByIdAndUserId(any(), any())).thenAnswer(call ->
			session.getId().equals(call.getArgument(0)) && session.getUserId().equals(call.getArgument(1)) ? Optional.of(session) : Optional.empty());
		when(sessions.findById(chatId)).thenAnswer(call -> Optional.of(session));
		when(personas.contentOf(analysisId)).thenReturn(Optional.of(persona));
		when(replies.tryLock(chatId)).thenReturn(true);

		when(reports.findBySessionId(chatId)).thenAnswer(call -> Optional.ofNullable(report));
		when(reports.saveAndFlush(any())).thenAnswer(call -> {
			report = call.getArgument(0);
			ReflectionTestUtils.setField(report, "id", UUID.randomUUID());
			return report;
		});
		when(reports.save(any())).thenAnswer(call -> {
			report = call.getArgument(0);
			return report;
		});
		when(messages.findBySessionIdOrderBySeqAsc(chatId)).thenAnswer(call -> store.stream().sorted(Comparator.comparingInt(ChatMessage::getSeq)).toList());
		when(messages.findBySessionIdOrderBySeqDesc(any(), any(Pageable.class))).thenAnswer(call -> store.stream()
			.sorted(Comparator.comparingInt(ChatMessage::getSeq).reversed()).limit(((Pageable) call.getArgument(1)).getPageSize()).toList());
		when(ai.complete(any())).thenReturn(new AiResult(GOOD_JSON, 100, 50));

		Executor executor = task -> {
			if (rejectExecutor) {
				throw new RejectedExecutionException("full");
			}
			queued.add(task);
		};
		service = new ReportService(sessions, messages, users, reports, personas, new ReportGenerator(ai), new SensitiveMasker(), replies,
			new TransactionTemplate(mock(PlatformTransactionManager.class)), executor);
	}

	/** 첫 인사 + 내 말 n개와 그 답변(1분 간격). 대화 시간은 2n분이에요. */
	private void conversation(int userMessages) {
		add(1, ChatRole.PERSONA, "어 왔어?", 0);
		for (int i = 1; i <= userMessages; i++) {
			add(2 * i, ChatRole.USER, "내 말 " + i, 2 * i - 1);
			add(2 * i + 1, ChatRole.PERSONA, "답변 " + i, 2 * i);
		}
	}

	private void add(int seq, ChatRole role, String text, int minutesAfterStart) {
		store.add(ChatMessage.create(chatId, seq, role, text, start.plusMinutes(minutesAfterStart)));
	}

	private void assertApi(Runnable call, HttpStatus status, String code) {
		assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
			assertThat(e.getStatus()).isEqualTo(status);
			assertThat(e.getCode()).isEqualTo(code);
		});
	}

	// ───────── 끝내기: 검증 ─────────

	@Test
	void 없는_대화이거나_남의_대화는_404예요() {
		conversation(2);

		assertApi(() -> service.end(UUID.randomUUID(), chatId), HttpStatus.NOT_FOUND, "NOT_FOUND");
		assertApi(() -> service.end(userId, UUID.randomUUID()), HttpStatus.NOT_FOUND, "NOT_FOUND");
		assertThat(session.isEnded()).isFalse();
	}

	@Test
	void AI_동의가_없으면_403이고_끝나지_않아요() {
		conversation(2);
		user.revokeAiConsent();

		assertApi(() -> service.end(userId, chatId), HttpStatus.FORBIDDEN, "AI_CONSENT_REQUIRED");
		assertThat(session.isEnded()).isFalse();
		assertThat(queued).isEmpty();
	}

	@Test
	void 준비_중이거나_실패한_대화는_409_CHAT_NOT_READY예요() {
		ChatSession preparing = ChatSession.start(userId, Kst.today().minusDays(30), PersonaScope.AROUND, null, RecordLevel.ENOUGH);
		ReflectionTestUtils.setField(preparing, "id", chatId);
		session = preparing;

		assertApi(() -> service.end(userId, chatId), HttpStatus.CONFLICT, "CHAT_NOT_READY");

		session.fail("AI_UNAVAILABLE");
		assertApi(() -> service.end(userId, chatId), HttpStatus.CONFLICT, "CHAT_NOT_READY");
		assertThat(session.isEnded()).isFalse();
	}

	@Test
	void 답변이_오는_중이면_409_CHAT_BUSY이고_남의_잠금은_풀지_않아요() {
		conversation(2);
		doReturn(false).when(replies).tryLock(chatId);

		assertApi(() -> service.end(userId, chatId), HttpStatus.CONFLICT, "CHAT_BUSY");

		assertThat(session.isEnded()).isFalse();
		verify(replies, never()).unlock(chatId);
	}

	// ───────── 끝내기: 정상 흐름 ─────────

	@Test
	void 내_말이_2개_이상이면_끝내고_리포트_만들기를_시작해요() {
		conversation(2);

		EndChatResult result = service.end(userId, chatId);

		assertThat(result.chatId()).isEqualTo(chatId);
		assertThat(result.reportStatus()).isEqualTo(ReportStatus.GENERATING);
		assertThat(result.durationMinutes()).isEqualTo(4); // 1분 간격으로 5개 메시지
		assertThat(result.endedAt()).isNotNull();
		assertThat(session.isEnded()).isTrue();
		assertThat(report.getStatus()).isEqualTo(ReportStatus.GENERATING);
		assertThat(queued).hasSize(1);
		verify(replies).unlock(chatId); // 끝내는 동안만 잠가요
	}

	@Test
	void 내_말이_2개_미만이면_끝나지만_리포트는_만들지_않아요() {
		conversation(1);

		EndChatResult result = service.end(userId, chatId);

		assertThat(result.reportStatus()).isEqualTo(ReportStatus.NONE);
		assertThat(session.isEnded()).isTrue();
		assertThat(report.getStatus()).isEqualTo(ReportStatus.NONE);
		assertThat(queued).isEmpty();
		assertApi(() -> service.get(userId, chatId), HttpStatus.NOT_FOUND, "REPORT_NOT_AVAILABLE");
	}

	@Test
	void 아무_말도_안_하고_끝내도_끝나요() {
		conversation(0);

		assertThat(service.end(userId, chatId).reportStatus()).isEqualTo(ReportStatus.NONE);
		assertThat(session.isEnded()).isTrue();
	}

	@Test
	void 이미_끝난_대화에_다시_불러도_같은_결과이고_다시_만들지_않아요() {
		conversation(2);
		EndChatResult first = service.end(userId, chatId);

		EndChatResult second = service.end(userId, chatId);

		assertThat(second).isEqualTo(first);
		assertThat(queued).hasSize(1); // 만들기는 한 번만 시작해요
		verify(reports, times(1)).saveAndFlush(any());
	}

	@Test
	void 실패한_리포트는_끝내기를_다시_부르면_다시_만들어요() {
		conversation(2);
		service.end(userId, chatId);
		report.fail("AI_UNAVAILABLE", Kst.now());

		EndChatResult retry = service.end(userId, chatId);

		assertThat(retry.reportStatus()).isEqualTo(ReportStatus.GENERATING);
		assertThat(report.getStatus()).isEqualTo(ReportStatus.GENERATING);
		assertThat(report.getFailureCode()).isNull();
		assertThat(queued).hasSize(2);
	}

	@Test
	void 만드는_중이_너무_오래되면_실패로_보고_다시_만들어요() {
		conversation(2);
		service.end(userId, chatId);
		ReflectionTestUtils.setField(report, "updatedAt", Kst.now().minusMinutes(11));

		EndChatResult retry = service.end(userId, chatId);

		assertThat(retry.reportStatus()).isEqualTo(ReportStatus.GENERATING);
		assertThat(queued).hasSize(2);
	}

	@Test
	void 아직_만드는_중이면_다시_시작하지_않아요() {
		conversation(2);
		service.end(userId, chatId);

		assertThat(service.end(userId, chatId).reportStatus()).isEqualTo(ReportStatus.GENERATING);
		assertThat(queued).hasSize(1);
	}

	@Test
	void 작업_줄이_가득_차면_503이고_리포트는_실패로_남아_다시_누를_수_있어요() {
		conversation(2);
		rejectExecutor = true;

		assertApi(() -> service.end(userId, chatId), HttpStatus.SERVICE_UNAVAILABLE, "SERVER_BUSY");

		assertThat(session.isEnded()).isTrue();
		assertThat(report.getStatus()).isEqualTo(ReportStatus.FAILED);
		assertThat(report.getFailureCode()).isEqualTo("SERVER_BUSY");
		verify(replies).unlock(chatId);

		rejectExecutor = false;
		assertThat(service.end(userId, chatId).reportStatus()).isEqualTo(ReportStatus.GENERATING);
		assertThat(queued).hasSize(1);
	}

	@Test
	void 다른_서버가_먼저_리포트_행을_만들었으면_그_결과를_돌려줘요() {
		conversation(2);
		Report other = Report.generating(chatId, 4, Kst.now());
		AtomicInteger lookups = new AtomicInteger();
		when(reports.findBySessionId(chatId)).thenAnswer(call -> lookups.getAndIncrement() == 0 ? Optional.empty() : Optional.of(other));
		doThrow(new DataIntegrityViolationException("reports_session_id_key")).when(reports).saveAndFlush(any());

		EndChatResult result = service.end(userId, chatId);

		assertThat(result.reportStatus()).isEqualTo(ReportStatus.GENERATING);
		assertThat(queued).isEmpty(); // 만들기는 먼저 만든 서버가 해요
		verify(replies).unlock(chatId);
	}

	// ───────── 리포트 만들기 ─────────

	@Test
	void 만들기가_끝나면_READY로_세_항목을_저장해요() {
		conversation(2);
		service.end(userId, chatId);

		queued.get(0).run();

		assertThat(report.getStatus()).isEqualTo(ReportStatus.READY);
		assertThat(report.getPastConcern()).isEqualTo("그때 고민");
		assertThat(report.getPresentChange()).isEqualTo("지금 변화");
		assertThat(report.getOneLineSummary()).isEqualTo("한 줄 요약");
	}

	@Test
	void AI로_가는_내_말은_가리고_과거의_나의_말은_그대로_보내요() {
		add(1, ChatRole.PERSONA, "연락처 알려줄래?", 0);
		add(2, ChatRole.USER, "내 번호는 010-1234-5678이야", 1);
		add(3, ChatRole.PERSONA, "고마워 ㅋㅋ", 2);
		add(4, ChatRole.USER, "메일은 a@b.com 이야", 3);
		add(5, ChatRole.PERSONA, "응", 4);
		service.end(userId, chatId);

		queued.get(0).run();

		ArgumentCaptor<AiRequest> request = ArgumentCaptor.forClass(AiRequest.class);
		verify(ai).complete(request.capture());
		String sent = request.getValue().messages().get(0).text();
		assertThat(sent).contains("[전화번호]").contains("[이메일]").doesNotContain("010-1234-5678").doesNotContain("a@b.com");
		assertThat(sent).contains("<turn speaker=\"past\">연락처 알려줄래?</turn>").contains("<turn speaker=\"past\">고마워 ㅋㅋ</turn>");
		assertThat(sent).contains("고민: 진로 고민"); // 그 시점의 분석도 같이 가요
	}

	@Test
	void 분석이_없는_시점이면_과거의_나_분석_없이_대화만으로_만들어요() {
		session.ready(null);
		conversation(2);
		service.end(userId, chatId);

		queued.get(0).run();

		ArgumentCaptor<AiRequest> request = ArgumentCaptor.forClass(AiRequest.class);
		verify(ai).complete(request.capture());
		assertThat(request.getValue().messages().get(0).text()).doesNotContain("<past_self>");
		assertThat(report.getStatus()).isEqualTo(ReportStatus.READY);
	}

	@Test
	void 대화가_아주_길면_가장_최근_메시지만_보내요() {
		add(1, ChatRole.PERSONA, "첫 인사", 0);
		for (int i = 2; i <= 200; i++) {
			add(i, i % 2 == 0 ? ChatRole.USER : ChatRole.PERSONA, "말 " + i, i);
		}
		service.end(userId, chatId);

		queued.get(0).run();

		ArgumentCaptor<AiRequest> request = ArgumentCaptor.forClass(AiRequest.class);
		verify(ai).complete(request.capture());
		String sent = request.getValue().messages().get(0).text();
		assertThat(sent.split("<turn ", -1).length - 1).isEqualTo(ReportService.MAX_CONVERSATION_MESSAGES);
		assertThat(sent).contains("말 200").doesNotContain("첫 인사");
	}

	@Test
	void AI_오류는_FAILED와_이유로_남겨요() {
		conversation(2);
		service.end(userId, chatId);
		doThrow(new AiException(AiException.Reason.UNAVAILABLE, "연결 안 됨")).when(ai).complete(any());

		queued.get(0).run();

		assertThat(report.getStatus()).isEqualTo(ReportStatus.FAILED);
		assertThat(report.getFailureCode()).isEqualTo("AI_UNAVAILABLE");
		assertApi(() -> service.get(userId, chatId), HttpStatus.BAD_GATEWAY, "REPORT_FAILED");
	}

	@Test
	void 거절되거나_JSON이_아니면_FAILED예요() {
		conversation(2);
		service.end(userId, chatId);
		doThrow(new AiException(AiException.Reason.REFUSED, "거절")).when(ai).complete(any());
		queued.get(0).run();
		assertThat(report.getFailureCode()).isEqualTo("AI_REFUSED");

		service.end(userId, chatId); // 재시도
		doReturn(new AiResult("죄송해요 못 만들겠어요", 10, 5)).when(ai).complete(any());
		queued.get(1).run();
		assertThat(report.getStatus()).isEqualTo(ReportStatus.FAILED);
		assertThat(report.getFailureCode()).isEqualTo("AI_BAD_RESPONSE");
	}

	@Test
	void 예상하지_못한_오류도_FAILED로_남기고_밖으로_던지지_않아요() {
		conversation(2);
		service.end(userId, chatId);
		doThrow(new IllegalStateException("복호화 실패")).when(personas).contentOf(analysisId);

		queued.get(0).run();

		assertThat(report.getStatus()).isEqualTo(ReportStatus.FAILED);
		assertThat(report.getFailureCode()).isEqualTo("INTERNAL_ERROR");
	}

	@Test
	void 만드는_도중_다시_시작됐어도_이미_완성된_리포트는_덮어쓰지_않아요() {
		conversation(2);
		service.end(userId, chatId);
		report.complete(new ReportContent("먼저 끝난 고민", "먼저 끝난 변화", "먼저 끝난 요약"), Kst.now());

		queued.get(0).run();

		assertThat(report.getPastConcern()).isEqualTo("먼저 끝난 고민");
		verify(ai, times(1)).complete(any()); // 이미 끝난 것을 모르고 AI는 불렀지만 저장은 하지 않아요
	}

	@Test
	void 세션이_지워졌으면_AI를_부르지_않아요() {
		conversation(2);
		service.end(userId, chatId);
		doReturn(Optional.empty()).when(sessions).findById(chatId);

		queued.get(0).run();

		verify(ai, never()).complete(any());
	}

	// ───────── 리포트 조회 ─────────

	@Test
	void 끝나지_않은_대화의_리포트는_404_CHAT_NOT_ENDED예요() {
		conversation(2);

		assertApi(() -> service.get(userId, chatId), HttpStatus.NOT_FOUND, "CHAT_NOT_ENDED");
	}

	@Test
	void 남의_대화의_리포트는_404예요() {
		conversation(2);
		service.end(userId, chatId);

		assertApi(() -> service.get(UUID.randomUUID(), chatId), HttpStatus.NOT_FOUND, "NOT_FOUND");
	}

	@Test
	void 만드는_중이면_빈_값이에요() {
		conversation(2);
		service.end(userId, chatId);

		assertThat(service.get(userId, chatId)).isEmpty();
	}

	@Test
	void 완성되면_명세의_Report_모양으로_줘요() {
		conversation(2);
		service.end(userId, chatId);
		queued.get(0).run();

		ReportResponse response = service.get(userId, chatId).orElseThrow();

		assertThat(response.id()).isNotNull();
		assertThat(response.chatId()).isEqualTo(chatId);
		assertThat(response.mode()).isEqualTo(ChatMode.PERSONAL);
		assertThat(response.scope()).isEqualTo(PersonaScope.AROUND);
		assertThat(response.anchorDate()).isEqualTo(session.getAnchorDate());
		assertThat(response.durationMinutes()).isEqualTo(4);
		assertThat(response.pastConcern()).isEqualTo("그때 고민");
		assertThat(response.presentChange()).isEqualTo("지금 변화");
		assertThat(response.oneLineSummary()).isEqualTo("한 줄 요약");
		assertThat(response.memorySummary()).isNull();
		assertThat(response.createdAt().getOffset()).isEqualTo(Kst.now().getOffset());
	}

	@Test
	void 오래된_만드는_중은_조회할_때_실패로_바뀌고_그_상태가_남아요() {
		conversation(2);
		service.end(userId, chatId);
		ReflectionTestUtils.setField(report, "updatedAt", Kst.now().minusMinutes(11));

		assertApi(() -> service.get(userId, chatId), HttpStatus.BAD_GATEWAY, "REPORT_FAILED");

		assertThat(report.getStatus()).isEqualTo(ReportStatus.FAILED);
		assertThat(report.getFailureCode()).isEqualTo("REPORT_TIMEOUT");
		verify(reports).save(report);
	}

	@Test
	void 끝났는데_리포트_행이_없으면_404_REPORT_NOT_AVAILABLE이에요() {
		session.end(Kst.now());

		assertApi(() -> service.get(userId, chatId), HttpStatus.NOT_FOUND, "REPORT_NOT_AVAILABLE");
	}
}
