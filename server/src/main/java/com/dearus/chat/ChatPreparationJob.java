package com.dearus.chat;

import com.dearus.common.ai.AiException;
import com.dearus.persona.PersonaAnalysisService;
import com.dearus.persona.PersonaAnalysisService.Result;
import com.dearus.persona.PersonaAnalysisService.Step;
import com.dearus.persona.PersonaScope;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PREPARING인 세션의 시점 분석을 뒤에서 하고, 끝나면 세션을 READY로 바꾸면서 첫 인사(seq 1)를 저장해요.
 * 실패하면 FAILED + failureCode예요(AI_UNAVAILABLE 등). 진행 단계는 메모리에만 두어서, 서버가 다시 켜지면 단계는 없어지고
 * 세션은 10분이 지나면 조회할 때 FAILED가 돼요. 로그에는 글자 없이 세션 ID와 이유만 남겨요.
 */
@Component
public class ChatPreparationJob {

	private static final Logger log = LoggerFactory.getLogger(ChatPreparationJob.class);

	private final PersonaAnalysisService persona;
	private final ChatSessionRepository sessions;
	private final ChatMessageRepository messages;
	private final TransactionTemplate tx;
	private final ConcurrentHashMap<UUID, Step> steps = new ConcurrentHashMap<>();

	public ChatPreparationJob(PersonaAnalysisService persona, ChatSessionRepository sessions, ChatMessageRepository messages,
		TransactionTemplate tx) {
		this.persona = persona;
		this.sessions = sessions;
		this.messages = messages;
		this.tx = tx;
	}

	/** 지금 진행 중인 단계예요. 모르면 null. */
	public Step stepOf(UUID sessionId) {
		return steps.get(sessionId);
	}

	/** 작업을 줄에 넣기 전에 불러서, 만든 직후 조회해도 첫 단계가 보이게 해요. */
	public void begin(UUID sessionId) {
		steps.put(sessionId, Step.COLLECT);
	}

	public void run(UUID sessionId, UUID userId, PersonaScope scope, LocalDate anchor) {
		try {
			Optional<Result> result = persona.getOrCreate(userId, scope, anchor, step -> steps.put(sessionId, step));
			tx.executeWithoutResult(status -> finish(sessionId, result.orElse(null)));
		} catch (AiException e) {
			log.warn("대화 준비 실패 sessionId={} reason={} detail={}", sessionId, e.reason(), e.getMessage());
			fail(sessionId, e.reason().code());
		} catch (RuntimeException e) {
			log.error("대화 준비 중 예상하지 못한 오류 sessionId={}", sessionId, e);
			fail(sessionId, "INTERNAL_ERROR");
		} finally {
			steps.remove(sessionId);
		}
	}

	/** 분석이 끝났어요. 그 사이 기록이 모두 지워져서 결과가 없으면(null) 분석 없이 기본 말투로 시작해요. */
	private void finish(UUID sessionId, Result result) {
		ChatSession session = sessions.findById(sessionId).orElse(null);
		if (session == null || !session.isPreparing()) {
			return; // 그 사이 지워졌거나(탈퇴) 이미 실패로 처리된 세션이에요
		}
		session.ready(result == null ? null : result.analysisId());
		sessions.save(session);
		messages.save(ChatMessage.create(sessionId, 1, ChatRole.PERSONA,
			ChatGreetings.pick(result == null ? null : result.content().greetings())));
		log.info("대화 준비 완료 sessionId={} reused={}", sessionId, result != null && result.reused());
	}

	private void fail(UUID sessionId, String failureCode) {
		try {
			tx.executeWithoutResult(status -> sessions.findById(sessionId).ifPresent(session -> {
				if (session.isPreparing()) {
					session.fail(failureCode);
					sessions.save(session);
				}
			}));
		} catch (RuntimeException e) {
			// 여기서도 실패하면 조회할 때 10분이 지난 PREPARING을 FAILED로 바꿔요
			log.error("실패 상태를 저장하지 못했어요 sessionId={}", sessionId, e);
		}
	}
}
