package com.dearus.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.dearus.auth.User;
import com.dearus.auth.UserRepository;
import com.dearus.chat.ChatMessage;
import com.dearus.chat.ChatMessageRepository;
import com.dearus.chat.ChatRole;
import com.dearus.chat.ChatSession;
import com.dearus.chat.ChatSessionRepository;
import com.dearus.persona.PersonaScope;
import com.dearus.persona.RecordLevel;
import com.dearus.report.Report;
import com.dearus.report.ReportRepository;
import com.dearus.upload.MessengerSource;
import com.dearus.upload.MessengerUpload;
import com.dearus.upload.MessengerUploadRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 진짜 DB(server/.env의 Supabase)에서 하루 상한에 쓰는 개수 쿼리가 오늘(한국 시간) · 내 것 · 셀 것만 세는지 확인해요(2026.11.25).
 * DB를 건드려서 DEARUS_DB_TEST=true일 때만 돌고, 만든 사용자는 끝나면 지워요.
 * 돌리기: DEARUS_DB_TEST=true ./gradlew test --tests '*UsageLimitDbTest' -i
 */
@EnabledIfEnvironmentVariable(named = "DEARUS_DB_TEST", matches = "true")
@SpringBootTest(properties = "app.ai.provider=fake")
class UsageLimitDbTest {

	@Autowired UserRepository users;
	@Autowired ChatSessionRepository sessions;
	@Autowired ChatMessageRepository messages;
	@Autowired MessengerUploadRepository uploads;
	@Autowired ReportRepository reports;

	private UUID newUser(String name) {
		User user = User.create("dev", "dbtest-" + UUID.randomUUID(), name);
		user.agree(false);
		return users.saveAndFlush(user).getId();
	}

	private UUID newSession(UUID userId) {
		return sessions.saveAndFlush(ChatSession.start(userId, LocalDate.of(2026, 5, 1), PersonaScope.DAY, null, RecordLevel.ENOUGH)).getId();
	}

	@Test
	void 오늘_내가_쓴_것만_세요() {
		UUID me = newUser("나");
		UUID other = newUser("남");
		try {
			OffsetDateTime today = DailyLimit.startOfToday();
			OffsetDateTime yesterday = today.minusHours(1);

			UUID chat = newSession(me);
			messages.saveAndFlush(ChatMessage.create(chat, 1, ChatRole.PERSONA, "안녕", today.plusMinutes(1)));
			messages.saveAndFlush(ChatMessage.create(chat, 2, ChatRole.USER, "어제 한 말", yesterday));
			messages.saveAndFlush(ChatMessage.create(chat, 3, ChatRole.PERSONA, "답", yesterday));
			messages.saveAndFlush(ChatMessage.create(chat, 4, ChatRole.USER, "오늘 한 말 1", today.plusMinutes(2)));
			messages.saveAndFlush(ChatMessage.create(chat, 5, ChatRole.USER, "오늘 한 말 2", today.plusMinutes(3)));
			UUID otherChat = newSession(other);
			messages.saveAndFlush(ChatMessage.create(otherChat, 1, ChatRole.USER, "남이 한 말", today.plusMinutes(1)));

			assertThat(messages.countUserMessagesSince(me, today)).isEqualTo(2);
			assertThat(messages.countBySessionIdAndRole(chat, ChatRole.USER)).isEqualTo(3);

			MessengerUpload old = MessengerUpload.receive(me, MessengerSource.KAKAOTALK, 0);
			ReflectionTestUtils.setField(old, "uploadedAt", yesterday);
			uploads.saveAndFlush(old);
			uploads.saveAndFlush(MessengerUpload.receive(me, MessengerSource.KAKAOTALK, 0));
			uploads.saveAndFlush(MessengerUpload.receive(other, MessengerSource.KAKAOTALK, 0));
			assertThat(uploads.countByUserIdAndUploadedAtGreaterThanEqual(me, today)).isEqualTo(1);

			// 리포트는 AI로 만든 것만 세요(만들지 않은 NONE은 빼요)
			reports.saveAndFlush(Report.generating(chat, 3, Kst.now()));
			reports.saveAndFlush(Report.none(newSession(me), 0, Kst.now()));
			reports.saveAndFlush(Report.generating(newSession(me), 2, yesterday));
			reports.saveAndFlush(Report.generating(otherChat, 1, Kst.now()));
			assertThat(reports.countGeneratedSince(me, today)).isEqualTo(1);
		} finally {
			users.deleteById(me);
			users.deleteById(other);
		}
	}
}
