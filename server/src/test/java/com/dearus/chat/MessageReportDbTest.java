package com.dearus.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.dearus.auth.User;
import com.dearus.auth.UserRepository;
import com.dearus.persona.PersonaScope;
import com.dearus.persona.RecordLevel;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 진짜 DB(server/.env의 Supabase)에서 답변 신고가 저장되고(V11), 다시 신고해도 한 행이고, 대화가 지워지면 신고도 함께 지워지는지 확인해요.
 * DB를 건드려서 DEARUS_DB_TEST=true일 때만 돌고, 만든 사용자는 끝나면 지워요.
 * 돌리기: DEARUS_DB_TEST=true ./gradlew test --tests '*MessageReportDbTest' -i
 */
@EnabledIfEnvironmentVariable(named = "DEARUS_DB_TEST", matches = "true")
@SpringBootTest(properties = "app.ai.provider=fake")
class MessageReportDbTest {

	@Autowired UserRepository users;
	@Autowired ChatSessionRepository sessions;
	@Autowired ChatMessageRepository messages;
	@Autowired MessageReportService service;
	@Autowired JdbcTemplate jdbc;

	@Test
	void 신고는_한_번만_저장되고_대화가_지워지면_함께_지워져요() {
		User user = User.create("dev", "dbtest-" + UUID.randomUUID(), "시험");
		user.agree(false);
		UUID userId = users.saveAndFlush(user).getId();
		try {
			ChatSession session = ChatSession.start(userId, LocalDate.of(2026, 5, 1), PersonaScope.DAY, null, RecordLevel.ENOUGH);
			UUID chatId = sessions.saveAndFlush(session).getId();
			messages.saveAndFlush(ChatMessage.create(chatId, 1, ChatRole.PERSONA, "안녕 나야"));
			messages.saveAndFlush(ChatMessage.create(chatId, 2, ChatRole.USER, "응 안녕"));
			Long answerId = messages.saveAndFlush(ChatMessage.create(chatId, 3, ChatRole.PERSONA, "이상한 답변")).getId();

			service.report(userId, chatId, 3, new MessageReportRequest(MessageReportReason.OTHER, "그때 나는 이렇게 말하지 않았어요"));
			// 다시 눌러도 처음 신고가 남아요
			service.report(userId, chatId, 3, new MessageReportRequest(MessageReportReason.HURTFUL, null));

			Integer count = jdbc.queryForObject("select count(*) from message_reports where message_id = ?", Integer.class, answerId);
			assertThat(count).isEqualTo(1);
			String reason = jdbc.queryForObject("select reason from message_reports where message_id = ?", String.class, answerId);
			assertThat(reason).isEqualTo("OTHER");

			// 대화가 지워지면(탈퇴 · 동의 철회 30일 뒤 · 커플 해제) 신고도 DB에서 함께 지워져요
			sessions.deleteById(chatId);
			sessions.flush();
			Integer left = jdbc.queryForObject("select count(*) from message_reports where user_id = ?", Integer.class, userId);
			assertThat(left).isZero();
		} finally {
			users.deleteById(userId);
		}
	}
}
