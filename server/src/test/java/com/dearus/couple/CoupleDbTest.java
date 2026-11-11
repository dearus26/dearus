package com.dearus.couple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dearus.auth.ConsentRequest;
import com.dearus.auth.ConsentService;
import com.dearus.auth.User;
import com.dearus.auth.UserRepository;
import com.dearus.chat.ChatCreateRequest;
import com.dearus.chat.ChatMode;
import com.dearus.chat.ChatReplyService;
import com.dearus.chat.ChatService;
import com.dearus.chat.ChatSessionRepository;
import com.dearus.chat.ChatSessionResponse;
import com.dearus.chat.ChatStatus;
import com.dearus.common.ApiException;
import com.dearus.common.Kst;
import com.dearus.diary.Diary;
import com.dearus.diary.DiaryRepository;
import com.dearus.diary.Emotion;
import com.dearus.persona.PersonaScope;
import com.dearus.report.ReportRepository;
import com.dearus.report.ReportResponse;
import com.dearus.report.ReportService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 진짜 DB(server/.env의 Supabase)에서 커플 모드를 처음부터 끝까지 확인하는 시험이에요.
 * 초대 코드 → 연결 → 양측 동의 → 함께 볼 날 → 각자 그날의 연인과 대화 · 끝내기 → 커플 리포트 → 간극 → 두 시점 비교 →
 * 연인의 AI 동의 철회 때 막힘 → 커플 철회(이 커플의 대화 · 리포트 삭제)까지예요.
 * 가짜 AI(app.ai.provider=fake)를 쓰고, 만든 사용자는 끝나면 지워요.
 * 돌리기: DEARUS_DB_TEST=true ./gradlew test --tests '*CoupleDbTest' -i
 */
@EnabledIfEnvironmentVariable(named = "DEARUS_DB_TEST", matches = "true")
@SpringBootTest(properties = "app.ai.provider=fake")
class CoupleDbTest {

	@Autowired UserRepository users;
	@Autowired DiaryRepository diaries;
	@Autowired ConsentService consent;
	@Autowired CoupleService couples;
	@Autowired CoupleDayService days;
	@Autowired CoupleComparisonService comparisons;
	@Autowired CoupleMemberRepository members;
	@Autowired ChatService chats;
	@Autowired ChatReplyService replies;
	@Autowired ReportService reports;
	@Autowired ReportRepository reportRepository;
	@Autowired ChatSessionRepository sessions;

	private UUID user(String nickname) {
		User user = User.create("dev", "dbtest-couple-" + UUID.randomUUID(), nickname);
		user.agree(false);
		return users.saveAndFlush(user).getId();
	}

	private static String code(ApiException e) {
		return e.getCode();
	}

	private ApiException apiError(Runnable action) {
		try {
			action.run();
		} catch (ApiException e) {
			return e;
		}
		throw new AssertionError("ApiException이 나야 하는데 나지 않았어요");
	}

	private ChatSessionResponse waitReady(UUID userId, UUID chatId) throws InterruptedException {
		for (int i = 0; i < 150; i++) {
			ChatSessionResponse session = chats.get(userId, chatId);
			if (session.status() != ChatStatus.PREPARING) {
				return session;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("대화 준비가 15초 안에 끝나지 않았어요");
	}

	private ReportResponse waitReport(UUID userId, UUID chatId) throws InterruptedException {
		for (int i = 0; i < 150; i++) {
			Optional<ReportResponse> report = reports.get(userId, chatId);
			if (report.isPresent()) {
				return report.get();
			}
			Thread.sleep(100);
		}
		throw new AssertionError("리포트가 15초 안에 만들어지지 않았어요");
	}

	private CoupleComparisonResponse waitComparison(UUID userId, LocalDate day, String status) throws InterruptedException {
		CoupleComparisonResponse last = null;
		for (int i = 0; i < 150; i++) {
			last = comparisons.compare(userId, day, PersonaScope.DAY);
			if (last.status().equals(status)) {
				return last;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("비교가 15초 안에 " + status + "가 되지 않았어요. 마지막 상태: " + (last == null ? null : last.status()));
	}

	/** 그날의 연인과 대화하고 끝내요(내 말 2개라 리포트를 만들어요). */
	private UUID talkAndEnd(UUID userId, LocalDate day) throws InterruptedException {
		ChatSessionResponse created = chats.create(userId, new ChatCreateRequest(day, PersonaScope.DAY, null, true));
		assertThat(created.mode()).isEqualTo(ChatMode.COUPLE);
		ChatSessionResponse ready = waitReady(userId, created.id());
		assertThat(ready.status()).isEqualTo(ChatStatus.READY);
		// 같은 날 아직 끝내지 않은 세션이 있으면 그걸 돌려줘요
		assertThat(chats.create(userId, new ChatCreateRequest(day, PersonaScope.DAY, null, true)).id()).isEqualTo(created.id());
		replies.run(replies.prepare(userId, created.id(), "그날 왜 그랬어?"), ChatReplyService.Sink.NONE);
		replies.run(replies.prepare(userId, created.id(), "그때 마음이 어땠는지 알고 싶어"), ChatReplyService.Sink.NONE);
		reports.end(userId, created.id());
		return created.id();
	}

	@Test
	void 초대부터_비교와_철회까지_커플_모드가_진짜_DB에서_돌아가요() throws Exception {
		UUID seoyeon = user("서연");
		UUID doyun = user("도윤");
		try {
			// 1) 연결 전: 상태 NONE, 접근은 0%
			assertThat(couples.state(seoyeon).status()).isEqualTo("NONE");
			assertThat(code(apiError(() -> days.days(seoyeon, Kst.today().minusDays(10), Kst.today())))).isEqualTo("COUPLE_ACCESS_DENIED");

			// 2) 서연이 코드를 만들어요. 다시 눌러도 같은 코드예요
			CoupleStateResponse invited = couples.invite(seoyeon);
			assertThat(invited.status()).isEqualTo("PENDING");
			assertThat(invited.myRole()).isEqualTo(CoupleRole.INVITER);
			String inviteCode = invited.invite().code();
			assertThat(inviteCode).matches(InviteCodes.FORMAT);
			assertThat(couples.invite(seoyeon).invite().code()).isEqualTo(inviteCode);

			// 3) 내 코드 · 없는 코드는 안 돼요
			assertThat(code(apiError(() -> couples.accept(seoyeon, inviteCode)))).isEqualTo("COUPLE_OWN_CODE");
			assertThat(code(apiError(() -> couples.accept(doyun, "DU-ZZZZ-ZZZZ")))).isEqualTo("COUPLE_CODE_INVALID");
			assertThat(code(apiError(() -> couples.accept(doyun, "abc")))).isEqualTo("VALIDATION_FAILED");

			// 4) 도윤이 소문자로 입력해도 연결돼요. 연결만 되고 아직 열리지 않아요
			CoupleStateResponse connected = couples.accept(doyun, inviteCode.toLowerCase());
			assertThat(connected.status()).isEqualTo("CONNECTED");
			assertThat(connected.myRole()).isEqualTo(CoupleRole.INVITEE);
			assertThat(connected.partner().nickname()).isEqualTo("서연");
			assertThat(connected.compareOpen()).isFalse();
			assertThat(code(apiError(() -> couples.invite(seoyeon)))).isEqualTo("ALREADY_IN_COUPLE");
			LocalDate day = Kst.today().minusDays(30);
			assertThat(code(apiError(() -> chats.create(seoyeon, new ChatCreateRequest(day, PersonaScope.DAY, null, true)))))
				.isEqualTo("COUPLE_ACCESS_DENIED");

			// 5) 한쪽만 동의하면 아직 닫혀 있고, 둘 다 동의하면 열려요
			assertThat(couples.consent(seoyeon).compareOpen()).isFalse();
			assertThat(code(apiError(() -> comparisons.compare(seoyeon, day, PersonaScope.DAY)))).isEqualTo("COUPLE_ACCESS_DENIED");
			CoupleStateResponse open = couples.consent(doyun);
			assertThat(open.compareOpen()).isTrue();
			assertThat(open.me().consented()).isTrue();
			assertThat(open.partner().consented()).isTrue();

			// 6) 함께 볼 날: 두 사람의 일기가 모두 있는 날만, 감정이 엇갈린 날은 추천
			diaries.saveAndFlush(Diary.create(seoyeon, day, "연락이 끊겨서 서운했다.", Set.of(Emotion.SAD)));
			diaries.saveAndFlush(Diary.create(doyun, day, "갑자기 연락이 안 돼서 불안했다.", Set.of(Emotion.ANXIOUS, Emotion.ANGRY)));
			diaries.saveAndFlush(Diary.create(seoyeon, day.minusDays(1), "혼자 쓴 날", Set.of(Emotion.JOY)));
			List<CoupleDayResponse> found = days.days(seoyeon, day.minusDays(5), day.plusDays(5)).items();
			assertThat(found).hasSize(1);
			assertThat(found.get(0).date()).isEqualTo(day);
			assertThat(found.get(0).myEmotions()).containsExactly(Emotion.SAD);
			assertThat(found.get(0).partnerEmotions()).containsExactly(Emotion.ANXIOUS, Emotion.ANGRY);
			assertThat(found.get(0).recommended()).isTrue();
			assertThat(comparisons.compare(seoyeon, day, PersonaScope.DAY).status()).isEqualTo("NOT_STARTED");

			// 7) 서연이 그날의 도윤과 대화하고 끝내요. 페르소나 주인은 도윤이에요
			UUID seoyeonChat = talkAndEnd(seoyeon, day);
			assertThat(sessions.findById(seoyeonChat).orElseThrow().getPersonaOwnerId()).isEqualTo(doyun);
			ReportResponse seoyeonReport = waitReport(seoyeon, seoyeonChat);
			assertThat(seoyeonReport.mode()).isEqualTo(ChatMode.COUPLE);
			assertThat(seoyeonReport.memorySummary()).startsWith("(가짜 기억)");
			assertThat(seoyeonReport.pastConcern()).isNull();
			assertThat(chats.list(seoyeon).items()).isEmpty(); // "과거의 나" 목록에는 커플 세션을 넣지 않아요

			CoupleComparisonResponse waiting = comparisons.compare(seoyeon, day, PersonaScope.DAY);
			assertThat(waiting.status()).isEqualTo("WAITING_PARTNER");
			assertThat(waiting.chatId()).isEqualTo(seoyeonChat);
			assertThat(waiting.partnerPastSelf().memorySummary()).startsWith("(가짜 기억)");
			assertThat(waiting.myPastSelf()).isNull();
			// 도윤은 아직 대화하지 않았어요. 서연의 이야기는 도윤이 끝내기 전에는 도윤에게 보이지 않아요
			CoupleComparisonResponse doyunBefore = comparisons.compare(doyun, day, PersonaScope.DAY);
			assertThat(doyunBefore.status()).isEqualTo("NOT_STARTED");
			assertThat(doyunBefore.myPastSelf()).isNull();

			// 8) 도윤도 그날의 서연과 대화하고 끝내면, 간극이 만들어지고 두 사람 모두 READY예요
			UUID doyunChat = talkAndEnd(doyun, day);
			waitReport(doyun, doyunChat);
			CoupleComparisonResponse ready = waitComparison(seoyeon, day, "READY");
			assertThat(ready.gapSummary()).startsWith("(가짜 간극)");
			assertThat(ready.partnerNickname()).isEqualTo("도윤");
			assertThat(ready.partnerPastSelf()).isNotNull();
			assertThat(ready.myPastSelf()).isNotNull();
			CoupleComparisonResponse doyunReady = comparisons.compare(doyun, day, PersonaScope.DAY);
			assertThat(doyunReady.status()).isEqualTo("READY");
			assertThat(doyunReady.gapSummary()).isEqualTo(ready.gapSummary());
			assertThat(reportRepository.findBySessionId(seoyeonChat).orElseThrow().getGapSummary()).isEqualTo(ready.gapSummary());

			// 9) 도윤이 AI 분석 동의를 철회하면, 서연은 그날의 도윤을 새로 만날 수 없어요(비교는 볼 수 있어요)
			consent.revokeAiConsent(doyun);
			ApiException blocked = apiError(() -> chats.create(seoyeon, new ChatCreateRequest(day, PersonaScope.DAY, null, true)));
			assertThat(code(blocked)).isEqualTo("COUPLE_ACCESS_DENIED");
			assertThat(blocked.getMessage()).contains("AI 분석 동의를 철회");
			assertThat(comparisons.compare(seoyeon, day, PersonaScope.DAY).status()).isEqualTo("READY");
			consent.agree(doyun, new ConsentRequest(true, true, true, null));

			// 10) 서연이 커플을 철회하면 이 커플의 대화와 리포트를 두 사람 것 모두 지우고, 둘 다 NONE이에요. 일기는 남아요
			assertThat(couples.revoke(seoyeon).status()).isEqualTo("NONE");
			assertThat(sessions.findById(seoyeonChat)).isEmpty();
			assertThat(sessions.findById(doyunChat)).isEmpty();
			assertThat(reportRepository.findBySessionId(seoyeonChat)).isEmpty();
			assertThat(couples.state(seoyeon).status()).isEqualTo("NONE");
			assertThat(couples.state(doyun).status()).isEqualTo("NONE");
			assertThat(members.findByUserIdAndRevokedAtIsNull(doyun)).isEmpty();
			assertThat(diaries.findByUserIdAndDiaryDate(seoyeon, day)).isPresent();
			assertThat(code(apiError(() -> days.days(seoyeon, day.minusDays(5), day.plusDays(5))))).isEqualTo("COUPLE_ACCESS_DENIED");

			// 11) 연결 전의 초대는 철회하면 취소돼요. 다른 코드를 입력하면 내 기다리던 초대도 취소돼요
			String doyunCode = couples.invite(doyun).invite().code();
			String seoyeonCode = couples.invite(seoyeon).invite().code();
			assertThat(couples.accept(seoyeon, doyunCode).status()).isEqualTo("CONNECTED");
			assertThat(code(apiError(() -> couples.accept(doyun, seoyeonCode)))).isEqualTo("COUPLE_CODE_INVALID"); // 서연의 초대는 취소됐어요
			assertThat(couples.revoke(doyun).status()).isEqualTo("NONE");
			couples.invite(doyun);
			assertThat(couples.revoke(doyun).status()).isEqualTo("NONE");
			assertThat(couples.state(doyun).status()).isEqualTo("NONE");
		} finally {
			couples.revoke(seoyeon);
			couples.revoke(doyun);
			users.deleteById(seoyeon); // 일기 · 커플 멤버 · 세션이 ON DELETE CASCADE로 함께 지워져요
			users.deleteById(doyun);
		}
	}
}
