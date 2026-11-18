package com.dearus.chat;

import com.dearus.common.ApiException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 부적절한 답변 신고(2026.11.18 처리 방식 확정). 사용자가 AI의 답변을 신고하면 저장만 하고,
 * Dear Us 관리자가 확인해 프롬프트를 고쳐요. 신고한 사람에게 따로 답하지 않아요. AI를 부르지 않아서 AI 동의는 보지 않아요.
 */
@Service
public class MessageReportService {

	private static final Logger log = LoggerFactory.getLogger(MessageReportService.class);

	private final ChatSessionRepository sessions;
	private final ChatMessageRepository messages;
	private final MessageReportRepository reports;

	public MessageReportService(ChatSessionRepository sessions, ChatMessageRepository messages, MessageReportRepository reports) {
		this.sessions = sessions;
		this.messages = messages;
		this.reports = reports;
	}

	/**
	 * 내 대화의 AI 답변을 신고해요. 남의 대화이거나 없는 메시지면 404, 내 말이면 400이에요.
	 * 같은 답변을 다시 신고하면 처음 신고를 그대로 둬요(동시에 두 번 눌러도 유일 키가 하나만 남겨요).
	 * 유일 키 충돌을 잡아 204로 끝내려고 이 메서드는 트랜잭션을 열지 않아요(저장은 저장소의 트랜잭션에서 따로 끝나요).
	 */
	public void report(UUID userId, UUID chatId, int seq, MessageReportRequest request) {
		String detail = request.detail() == null || request.detail().isBlank() ? null : request.detail().strip();
		if (request.reason() == MessageReportReason.OTHER && detail == null) {
			throw ApiException.validation("detail", "기타를 고르면 내용을 적어 주세요");
		}
		sessions.findByIdAndUserId(chatId, userId).orElseThrow(ApiException::notFound);
		ChatMessage message = messages.findBySessionIdAndSeq(chatId, seq).orElseThrow(ApiException::notFound);
		if (message.getRole() != ChatRole.PERSONA) {
			throw ApiException.validation("seq", "AI의 답변만 신고할 수 있어요");
		}
		if (reports.existsByMessageIdAndUserId(message.getId(), userId)) {
			return;
		}
		try {
			reports.saveAndFlush(MessageReport.create(message.getId(), userId, request.reason(), detail));
		} catch (DataIntegrityViolationException e) {
			// 거의 동시에 온 같은 신고예요. 먼저 저장된 것이 남아요
			return;
		}
		// 대화 내용과 적은 내용은 로그에 남기지 않아요
		log.info("답변 신고 접수: chat={} seq={} reason={}", chatId, seq, request.reason());
	}
}
