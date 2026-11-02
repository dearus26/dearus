package com.dearus.chat;

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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 내 말을 받아 과거의 내가 답하게 해요(POST /chats/{chatId}/messages).
 * prepare가 검증과 준비(이력 읽기 · 프롬프트 만들기 · 위기 신호 점검)를 하고, run이 AI를 불러 글자를 흘려보내고 저장해요.
 * 규칙:
 * - 답변이 끝나야 내 말과 답변을 한 번에 저장해요(seq 2개). 도중에 실패하면 아무것도 저장하지 않아요.
 * - 클라이언트가 끊겨도 run은 끝까지 돌아서 답변을 저장해요(앱이 다시 열릴 때 이어받아요).
 * - 같은 세션에서 앞 답변이 끝나기 전에 또 보내면 409 CHAT_BUSY예요(서버 안 메모리 잠금, DB의 (session_id, seq) 유일 키가 마지막 방어선이에요).
 * - AI를 부르는 동안에는 DB 연결을 잡지 않아요(연결 풀이 작아요). 저장할 때만 짧은 트랜잭션을 써요.
 * 로그에는 글자 없이 세션 ID와 시간 · 토큰 수만 남겨요.
 */
@Service
public class ChatReplyService {

	private static final Logger log = LoggerFactory.getLogger(ChatReplyService.class);

	/** 글자 조각과 위기 신호를 흘려보내는 곳이에요(SSE이면 이벤트로, JSON 응답이면 아무것도 안 해요). */
	public interface Sink {

		Sink NONE = new Sink() {
			@Override
			public void safety(SafetyNotice notice) {
			}

			@Override
			public void token(String text) {
			}
		};

		void safety(SafetyNotice notice);

		void token(String text);
	}

	/** 검증과 준비를 마친 요청이에요. run을 부르면 잠금이 풀리고, run을 못 부르면 release로 풀어요. */
	public record Prepared(UUID chatId, AiRequest request, String userText, OffsetDateTime receivedAt, int nextSeq, SafetyNotice safety) {
	}

	private static final int HISTORY_FETCH = ChatPromptBuilder.MAX_HISTORY_MESSAGES + 2;

	private final ChatSessionRepository sessions;
	private final ChatMessageRepository messages;
	private final UserRepository users;
	private final PersonaAnalysisService persona;
	private final ChatPromptBuilder prompts;
	private final SafetyDetector detector;
	private final SafetyProperties safetyProperties;
	private final AiClient ai;
	private final TransactionTemplate tx;
	private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

	public ChatReplyService(ChatSessionRepository sessions, ChatMessageRepository messages, UserRepository users,
		PersonaAnalysisService persona, ChatPromptBuilder prompts, SafetyDetector detector, SafetyProperties safetyProperties,
		AiClient ai, TransactionTemplate tx) {
		this.sessions = sessions;
		this.messages = messages;
		this.users = users;
		this.persona = persona;
		this.prompts = prompts;
		this.detector = detector;
		this.safetyProperties = safetyProperties;
		this.ai = ai;
		this.tx = tx;
	}

	/** 오류는 일반 HTTP 오류로 나가요(스트림을 열기 전이라서요). 성공하면 같은 세션의 잠금을 잡은 채로 돌려줘요. */
	public Prepared prepare(UUID userId, UUID chatId, String content) {
		OffsetDateTime receivedAt = Kst.now();
		ChatSession session = sessions.findByIdAndUserId(chatId, userId).orElseThrow(ApiException::notFound);
		User user = users.findById(userId).orElseThrow(ApiException::unauthorized);
		if (!user.aiConsentActive()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "AI_CONSENT_REQUIRED", "AI 분석에 동의해야 쓸 수 있어요.");
		}
		if (session.getEndedAt() != null) {
			throw new ApiException(HttpStatus.CONFLICT, "CHAT_ENDED", "이미 끝난 대화예요.");
		}
		if (session.getStatus() != ChatStatus.READY) {
			throw new ApiException(HttpStatus.CONFLICT, "CHAT_NOT_READY", "아직 대화를 시작할 수 없어요.");
		}
		if (!inFlight.add(chatId)) {
			throw new ApiException(HttpStatus.CONFLICT, "CHAT_BUSY", "앞 답변이 아직 끝나지 않았어요. 잠시 뒤에 다시 보내 주세요.");
		}
		try {
			List<ChatMessage> recent = new ArrayList<>(messages.findBySessionIdOrderBySeqDesc(chatId, PageRequest.of(0, HISTORY_FETCH)));
			int nextSeq = recent.isEmpty() ? 1 : recent.get(0).getSeq() + 1;
			Collections.reverse(recent); // seq 오름차순
			String greeting = messages.findFirstBySessionIdOrderBySeqAsc(chatId)
				.filter(first -> first.getRole() == ChatRole.PERSONA).map(ChatMessage::getContent).orElse(null);
			List<ChatMessage> history = recent.stream().filter(m -> m.getSeq() > 1).toList(); // 첫 인사는 시스템 프롬프트에 있어요
			PersonaContent content0 = session.getAnalysisId() == null ? null : persona.contentOf(session.getAnalysisId()).orElse(null);
			boolean distress = detector.detects(content);
			AiRequest request = prompts.build(session, content0, greeting, history, content, distress);
			return new Prepared(chatId, request, content, receivedAt, nextSeq, distress ? SafetyNotice.distress(safetyProperties) : null);
		} catch (RuntimeException e) {
			inFlight.remove(chatId);
			throw e;
		}
	}

	/** run을 부르지 못하는 경우(작업 줄이 가득 참 등)에 잠금을 풀어요. */
	public void release(Prepared prepared) {
		inFlight.remove(prepared.chatId());
	}

	/** AI를 불러 답하고 저장해요. 끝나면(실패해도) 잠금이 풀려요. AI 오류는 AiException으로 나가요. */
	public ChatReplyResponse run(Prepared prepared, Sink sink) {
		long started = System.nanoTime();
		try {
			if (prepared.safety() != null) {
				sink.safety(prepared.safety());
			}
			long[] firstToken = {-1};
			AiResult result = ai.stream(prepared.request(), text -> {
				if (firstToken[0] < 0) {
					firstToken[0] = (System.nanoTime() - started) / 1_000_000;
				}
				sink.token(text);
			});
			String reply = result.text().strip();
			if (reply.isEmpty()) {
				throw new AiException(AiException.Reason.BAD_RESPONSE, "AI의 답이 비어 있어요");
			}
			ChatMessage[] saved = save(prepared, reply);
			log.info("대화 답변 완료 sessionId={} firstTokenMs={} totalMs={} inputTokens={} outputTokens={} safety={}", prepared.chatId(),
				firstToken[0], (System.nanoTime() - started) / 1_000_000, result.inputTokens(), result.outputTokens(), prepared.safety() != null);
			return new ChatReplyResponse(ChatMessageResponse.from(saved[0]), ChatMessageResponse.from(saved[1]), prepared.safety());
		} finally {
			inFlight.remove(prepared.chatId());
		}
	}

	/** 내 말(받은 시각)과 답변을 한 트랜잭션에 저장해요. 같은 seq가 이미 있으면(다른 서버가 먼저 저장) CHAT_BUSY예요. */
	private ChatMessage[] save(Prepared prepared, String reply) {
		try {
			return tx.execute(status -> {
				ChatMessage user = messages.save(ChatMessage.create(prepared.chatId(), prepared.nextSeq(), ChatRole.USER, prepared.userText(),
					prepared.receivedAt()));
				ChatMessage answer = messages.save(ChatMessage.create(prepared.chatId(), prepared.nextSeq() + 1, ChatRole.PERSONA, reply));
				return new ChatMessage[] {user, answer};
			});
		} catch (DataIntegrityViolationException e) {
			throw new ApiException(HttpStatus.CONFLICT, "CHAT_BUSY", "같은 대화에 동시에 보내서 저장하지 못했어요. 다시 보내 주세요.");
		}
	}

	/** JSON 응답일 때 AI 오류를 503 오류로 바꿔요(SSE에서는 error 이벤트로 같은 code를 써요). */
	public static ApiException toApiException(AiException e) {
		return switch (e.reason()) {
			case UNAVAILABLE -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, e.reason().code(), "지금은 AI에 연결할 수 없어요. 잠시 뒤에 다시 해 주세요.");
			case REFUSED -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, e.reason().code(), "이 이야기에는 답하기 어려워요. 다른 이야기를 해 볼까요?");
			case BAD_RESPONSE -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, e.reason().code(), "답변을 만들지 못했어요. 다시 보내 주세요.");
		};
	}
}
