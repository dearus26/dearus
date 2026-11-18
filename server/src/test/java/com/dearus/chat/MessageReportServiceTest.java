package com.dearus.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dearus.common.ApiException;
import com.dearus.persona.PersonaScope;
import com.dearus.persona.RecordLevel;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/** 답변 신고의 규칙(AI 답변만 · 기타는 내용 필수 · 남의 대화는 404 · 다시 신고해도 하나만)을 DB 없이 확인해요. */
class MessageReportServiceTest {

	private final ChatSessionRepository sessions = mock(ChatSessionRepository.class);
	private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
	private final MessageReportRepository reports = mock(MessageReportRepository.class);
	private final MessageReportService service = new MessageReportService(sessions, messages, reports);

	private final UUID userId = UUID.randomUUID();
	private final UUID chatId = UUID.randomUUID();

	private void myChatWith(int seq, ChatRole role) {
		ChatSession session = ChatSession.start(userId, LocalDate.of(2026, 5, 1), PersonaScope.DAY, null, RecordLevel.ENOUGH);
		when(sessions.findByIdAndUserId(chatId, userId)).thenReturn(Optional.of(session));
		ChatMessage message = ChatMessage.create(chatId, seq, role, "답변");
		ReflectionTestUtils.setField(message, "id", 77L);
		when(messages.findBySessionIdAndSeq(chatId, seq)).thenReturn(Optional.of(message));
	}

	private static String code(Throwable e) {
		return ((ApiException) e).getCode();
	}

	@Test
	void AI의_답변을_신고하면_사유와_적은_내용을_저장해요() {
		myChatWith(2, ChatRole.PERSONA);

		service.report(userId, chatId, 2, new MessageReportRequest(MessageReportReason.HURTFUL, "  너무 차갑게 말했어요  "));

		ArgumentCaptor<MessageReport> saved = ArgumentCaptor.forClass(MessageReport.class);
		verify(reports).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getMessageId()).isEqualTo(77L);
		assertThat(saved.getValue().getUserId()).isEqualTo(userId);
		assertThat(saved.getValue().getReason()).isEqualTo(MessageReportReason.HURTFUL);
		assertThat(saved.getValue().getDetail()).isEqualTo("너무 차갑게 말했어요");
		assertThat(saved.getValue().getReviewedAt()).isNull();
	}

	@Test
	void 빈_내용은_없는_것으로_저장해요() {
		myChatWith(1, ChatRole.PERSONA);

		service.report(userId, chatId, 1, new MessageReportRequest(MessageReportReason.INACCURATE, "   "));

		ArgumentCaptor<MessageReport> saved = ArgumentCaptor.forClass(MessageReport.class);
		verify(reports).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getDetail()).isNull();
	}

	@Test
	void 기타를_고르고_내용이_없으면_400이에요() {
		myChatWith(2, ChatRole.PERSONA);

		assertThatThrownBy(() -> service.report(userId, chatId, 2, new MessageReportRequest(MessageReportReason.OTHER, " ")))
			.satisfies(e -> assertThat(code(e)).isEqualTo("VALIDATION_FAILED"));
		verify(reports, never()).saveAndFlush(any());
	}

	@Test
	void 내_말은_신고할_수_없어요() {
		myChatWith(3, ChatRole.USER);

		assertThatThrownBy(() -> service.report(userId, chatId, 3, new MessageReportRequest(MessageReportReason.HARMFUL, null)))
			.satisfies(e -> assertThat(code(e)).isEqualTo("VALIDATION_FAILED"));
		verify(reports, never()).saveAndFlush(any());
	}

	@Test
	void 남의_대화나_없는_메시지는_404예요() {
		when(sessions.findByIdAndUserId(chatId, userId)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.report(userId, chatId, 2, new MessageReportRequest(MessageReportReason.PRIVACY, null)))
			.satisfies(e -> assertThat(code(e)).isEqualTo("NOT_FOUND"));

		myChatWith(2, ChatRole.PERSONA);
		when(messages.findBySessionIdAndSeq(chatId, 9)).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.report(userId, chatId, 9, new MessageReportRequest(MessageReportReason.PRIVACY, null)))
			.satisfies(e -> assertThat(code(e)).isEqualTo("NOT_FOUND"));
	}

	@Test
	void 이미_신고한_답변은_다시_저장하지_않아요() {
		myChatWith(2, ChatRole.PERSONA);
		when(reports.existsByMessageIdAndUserId(77L, userId)).thenReturn(true);

		service.report(userId, chatId, 2, new MessageReportRequest(MessageReportReason.HURTFUL, null));

		verify(reports, never()).saveAndFlush(any());
	}

	@Test
	void 거의_동시에_온_같은_신고는_조용히_넘겨요() {
		myChatWith(2, ChatRole.PERSONA);
		when(reports.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_message_reports_message_user"));

		service.report(userId, chatId, 2, new MessageReportRequest(MessageReportReason.HURTFUL, null));
	}
}
