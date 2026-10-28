package com.dearus.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.dearus.auth.ConsentService;
import com.dearus.auth.User;
import com.dearus.auth.UserRepository;
import com.dearus.common.Kst;
import com.dearus.common.Weeks;
import com.dearus.diary.Diary;
import com.dearus.diary.DiaryRepository;
import com.dearus.diary.Emotion;
import com.dearus.persona.PersonaAnalysisRepository;
import com.dearus.persona.PersonaScope;
import com.dearus.persona.RecordLevel;
import com.dearus.persona.ShortcutKey;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 진짜 DB(server/.env의 Supabase)에서 대화 세션이 만들어지고, 뒤에서 분석이 돌아 READY가 되고, 목록 · 메시지가 읽히고,
 * 동의를 철회해도 대화 기록은 남는지(분석 행만 지워지고 analysis_id는 비워짐) 확인하는 시험이에요.
 * DB를 건드려서 DEARUS_DB_TEST=true일 때만 돌고, 가짜 AI를 쓰며, 만든 사용자는 끝나면 지워요.
 * 돌리기: DEARUS_DB_TEST=true ./gradlew test --tests '*ChatSessionDbTest' -i
 */
@EnabledIfEnvironmentVariable(named = "DEARUS_DB_TEST", matches = "true")
@SpringBootTest(properties = "app.ai.provider=fake")
class ChatSessionDbTest {

	@Autowired UserRepository users;
	@Autowired DiaryRepository diaries;
	@Autowired ChatService chats;
	@Autowired ChatSessionRepository sessions;
	@Autowired ChatMessageRepository messages;
	@Autowired PersonaAnalysisRepository analyses;
	@Autowired ConsentService consent;

	private ChatSessionResponse waitUntilDone(UUID userId, UUID chatId) throws InterruptedException {
		ChatSessionResponse response = chats.get(userId, chatId);
		for (int i = 0; i < 100 && response.status() == ChatStatus.PREPARING; i++) {
			Thread.sleep(100);
			response = chats.get(userId, chatId);
		}
		return response;
	}

	@Test
	void 세션을_만들고_뒤에서_분석해_READY로_바꾸고_철회해도_대화는_남아요() throws Exception {
		User user = User.create("dev", "dbtest-" + UUID.randomUUID(), "시험");
		user.agree(false);
		UUID userId = users.saveAndFlush(user).getId();
		try {
			LocalDate anchor = Weeks.startOf(Kst.today().minusDays(60));

			// 1) 기록이 없는 시점: 분석 없이 바로 READY + 기본 인사
			ChatSessionResponse none = chats.create(userId, new ChatCreateRequest(anchor, PersonaScope.DAY, null, false));
			assertThat(none.status()).isEqualTo(ChatStatus.READY);
			assertThat(none.level()).isEqualTo(RecordLevel.NONE);
			assertThat(sessions.findById(none.id()).orElseThrow().getAnalysisId()).isNull();
			assertThat(chats.messages(userId, none.id(), 0, 50).items()).hasSize(1);

			// 2) 기록이 있는 시점: PREPARING으로 시작해 뒤에서 분석하고 READY가 돼요
			for (int i = 0; i < 3; i++) {
				diaries.saveAndFlush(Diary.create(userId, anchor.plusDays(i), "일기 " + i + " 내 번호 010-1234-5678", Set.of(Emotion.TIRED)));
			}
			ChatSessionResponse created = chats.create(userId, new ChatCreateRequest(anchor, PersonaScope.AROUND, ShortcutKey.ONE_MONTH, false));
			assertThat(created.level()).isEqualTo(RecordLevel.LOW); // 일기 3건, 메신저 없음
			assertThat(created.mode()).isEqualTo(ChatMode.PERSONAL);
			assertThat(created.shortcutKey()).isEqualTo(ShortcutKey.ONE_MONTH);
			ChatSessionResponse ready = waitUntilDone(userId, created.id());
			assertThat(ready.status()).isEqualTo(ChatStatus.READY);
			assertThat(ready.failureCode()).isNull();
			UUID analysisId = sessions.findById(created.id()).orElseThrow().getAnalysisId();
			assertThat(analysisId).isNotNull();
			assertThat(analyses.findById(analysisId)).isPresent();
			assertThat(chats.messages(userId, created.id(), 0, 50).items()).hasSize(1);
			assertThat(chats.messages(userId, created.id(), 0, 50).items().get(0).content()).startsWith("(가짜 인사)");

			// 3) 같은 시점을 또 열면 저장된 분석을 재사용해서 바로 READY (세션은 따로 생겨요)
			ChatSessionResponse again = chats.create(userId, new ChatCreateRequest(anchor, PersonaScope.AROUND, ShortcutKey.ONE_MONTH, false));
			assertThat(again.status()).isEqualTo(ChatStatus.READY);
			assertThat(again.id()).isNotEqualTo(created.id());
			assertThat(sessions.findById(again.id()).orElseThrow().getAnalysisId()).isEqualTo(analysisId);

			// 4) 목록: 세 세션, 최근 순, 하루 세션은 그날의 감정(일기가 없는 날이라 빈 목록)
			ChatListResponse list = chats.list(userId);
			assertThat(list.items()).hasSize(3);
			assertThat(list.items().get(0).id()).isEqualTo(again.id());
			assertThat(list.items().get(0).lastMessagePreview()).startsWith("(가짜 인사)");

			// 5) 동의를 철회하면 분석은 지워지지만 대화(세션 · 메시지)는 남고 analysis_id만 비워져요
			consent.revokeAiConsent(userId);
			assertThat(analyses.findById(analysisId)).isEmpty();
			assertThat(sessions.findById(created.id()).orElseThrow().getAnalysisId()).isNull();
			assertThat(sessions.findById(again.id()).orElseThrow().getAnalysisId()).isNull();
			assertThat(chats.messages(userId, created.id(), 0, 50).items()).hasSize(1);
			assertThat(chats.list(userId).items()).hasSize(3);
		} finally {
			users.deleteById(userId); // 세션 · 메시지 · 일기 · 분석이 ON DELETE CASCADE로 함께 지워져요
		}
		assertThat(sessions.findByUserIdAndCoupleIdIsNull(userId)).isEmpty();
	}
}
