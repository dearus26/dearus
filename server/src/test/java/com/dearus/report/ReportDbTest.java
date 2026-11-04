package com.dearus.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dearus.auth.ConsentPurgeJob;
import com.dearus.auth.ConsentService;
import com.dearus.auth.User;
import com.dearus.auth.UserRepository;
import com.dearus.chat.ChatCreateRequest;
import com.dearus.chat.ChatReplyService;
import com.dearus.chat.ChatService;
import com.dearus.chat.ChatSessionRepository;
import com.dearus.chat.ChatSessionResponse;
import com.dearus.chat.ChatStatus;
import com.dearus.chat.ChatUsageService;
import com.dearus.chat.UsageResponse;
import com.dearus.common.ApiException;
import com.dearus.common.Kst;
import com.dearus.persona.PersonaScope;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 진짜 DB(server/.env의 Supabase)에서 대화 끝내기 → 리포트 만들기(뒤에서) → 조회, 오늘 대화 시간(SQL), 끝낸 대화에는 말 못 걸기,
 * 짧은 대화의 NONE, 동의 철회 뒤 30일 폐기 때 리포트도 함께 지워지는지 확인하는 시험이에요.
 * 가짜 AI(app.ai.provider=fake)를 쓰고, 만든 사용자는 끝나면 지워요.
 * 돌리기: DEARUS_DB_TEST=true ./gradlew test --tests '*ReportDbTest' -i
 */
@EnabledIfEnvironmentVariable(named = "DEARUS_DB_TEST", matches = "true")
@SpringBootTest(properties = "app.ai.provider=fake")
class ReportDbTest {

	@Autowired UserRepository users;
	@Autowired ChatService chats;
	@Autowired ChatReplyService replies;
	@Autowired ReportService reportService;
	@Autowired ReportRepository reportRepository;
	@Autowired ChatSessionRepository sessions;
	@Autowired ChatUsageService usage;
	@Autowired ConsentService consent;
	@Autowired ConsentPurgeJob purge;

	/** 기록이 없는 시점이라 분석 없이 바로 READY인 대화를 만들고, 내 말을 userMessages번 해요(가짜 AI가 답해요). */
	private UUID chatWith(UUID userId, int daysAgo, int userMessages) {
		ChatSessionResponse session = chats.create(userId, new ChatCreateRequest(Kst.today().minusDays(daysAgo), PersonaScope.DAY, null, false));
		assertThat(session.status()).isEqualTo(ChatStatus.READY);
		for (int i = 1; i <= userMessages; i++) {
			replies.run(replies.prepare(userId, session.id(), "내 말 " + i), ChatReplyService.Sink.NONE);
		}
		return session.id();
	}

	private ReportResponse waitForReport(UUID userId, UUID chatId) throws InterruptedException {
		for (int i = 0; i < 100; i++) {
			Optional<ReportResponse> report = reportService.get(userId, chatId);
			if (report.isPresent()) {
				return report.get();
			}
			Thread.sleep(100);
		}
		throw new AssertionError("리포트가 10초 안에 만들어지지 않았어요");
	}

	@Test
	void 끝내기에서_리포트_조회까지_오늘_대화_시간_짧은_대화_철회_뒤_폐기를_확인해요() throws Exception {
		User user = User.create("dev", "dbtest-" + UUID.randomUUID(), "시험");
		user.agree(false);
		UUID userId = users.saveAndFlush(user).getId();
		try {
			UUID chatId = chatWith(userId, 50, 2);

			// 1) 안 끝난 대화의 리포트는 아직 없어요
			assertThatThrownBy(() -> reportService.get(userId, chatId))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("CHAT_NOT_ENDED"));

			// 2) 오늘 대화 시간: 메시지 5개가 거의 동시에 오가서 아주 짧지만 대화했으니 1분이에요 (SQL이 진짜 DB에서 도는지 확인)
			UsageResponse before = usage.today(userId);
			assertThat(before.date()).isEqualTo(Kst.today());
			assertThat(before.chatMinutes()).isEqualTo(1);
			assertThat(before.recommendedMinutes()).isEqualTo(30);

			// 3) 끝내기: 바로 돌아오고 리포트는 뒤에서 만들어져요
			EndChatResult ended = reportService.end(userId, chatId);
			assertThat(ended.reportStatus()).isEqualTo(ReportStatus.GENERATING);
			assertThat(ended.endedAt()).isNotNull();
			assertThat(sessions.findById(chatId).orElseThrow().isEnded()).isTrue();

			ReportResponse report = waitForReport(userId, chatId);
			assertThat(report.chatId()).isEqualTo(chatId);
			assertThat(report.scope()).isEqualTo(PersonaScope.DAY);
			assertThat(report.pastConcern()).startsWith("(가짜 리포트)");
			assertThat(report.presentChange()).startsWith("(가짜 리포트)");
			assertThat(report.oneLineSummary()).startsWith("(가짜 리포트)");
			assertThat(report.durationMinutes()).isEqualTo(ended.durationMinutes());
			assertThat(report.memorySummary()).isNull();

			// 4) 다시 끝내도 같은 결과예요(다시 만들지 않아요)
			EndChatResult again = reportService.end(userId, chatId);
			assertThat(again.reportStatus()).isEqualTo(ReportStatus.READY);
			assertThat(again.endedAt()).isEqualTo(ended.endedAt());
			assertThat(reportRepository.count()).isGreaterThanOrEqualTo(1);

			// 5) 끝난 대화에는 말을 걸 수 없어요
			assertThatThrownBy(() -> replies.prepare(userId, chatId, "또 말 걸기"))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("CHAT_ENDED"));

			// 6) 내 말이 1개뿐이면 끝낼 수 있지만 리포트는 없어요 (NONE)
			UUID shortChat = chatWith(userId, 51, 1);
			assertThat(reportService.end(userId, shortChat).reportStatus()).isEqualTo(ReportStatus.NONE);
			assertThatThrownBy(() -> reportService.get(userId, shortChat))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("REPORT_NOT_AVAILABLE"));

			// 7) 아무 말도 안 하고 끝내도 NONE이에요
			UUID silentChat = chatWith(userId, 52, 0);
			EndChatResult silent = reportService.end(userId, silentChat);
			assertThat(silent.reportStatus()).isEqualTo(ReportStatus.NONE);
			assertThat(silent.durationMinutes()).isZero();

			// 8) 동의를 철회하면 새로 끝낼 수 없지만, 이미 만든 리포트는 30일 동안 남아요
			UUID openChat = chatWith(userId, 53, 2);
			consent.revokeAiConsent(userId);
			assertThatThrownBy(() -> reportService.end(userId, openChat))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("AI_CONSENT_REQUIRED"));
			assertThat(reportService.get(userId, chatId)).isPresent();
			assertThat(purge.purgeIfExpired(userId, Kst.now())).isFalse();

			// 9) 30일이 지나면 대화와 함께 리포트도 지워져요 (ON DELETE CASCADE)
			User revoked = users.findById(userId).orElseThrow();
			ReflectionTestUtils.setField(revoked, "aiRevokedAt", Kst.now().minusDays(31));
			users.saveAndFlush(revoked);
			assertThat(purge.purgeIfExpired(userId, Kst.now())).isTrue();
			assertThat(reportRepository.findBySessionId(chatId)).isEmpty();
			assertThat(reportRepository.findBySessionId(shortChat)).isEmpty();
			assertThat(sessions.findById(chatId)).isEmpty();
		} finally {
			users.deleteById(userId); // 세션 · 메시지 · 리포트가 ON DELETE CASCADE로 함께 지워져요
		}
	}

	@Test
	void 회원_탈퇴하면_리포트도_함께_지워져요() throws Exception {
		User user = User.create("dev", "dbtest-" + UUID.randomUUID(), "시험");
		user.agree(false);
		UUID userId = users.saveAndFlush(user).getId();
		try {
			UUID chatId = chatWith(userId, 50, 2);
			reportService.end(userId, chatId);
			waitForReport(userId, chatId);
			assertThat(reportRepository.findBySessionId(chatId)).isPresent();

			users.deleteById(userId);
			users.flush();

			assertThat(reportRepository.findBySessionId(chatId)).isEmpty();
		} finally {
			if (users.existsById(userId)) {
				users.deleteById(userId);
			}
		}
	}
}
