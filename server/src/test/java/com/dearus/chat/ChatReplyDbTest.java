package com.dearus.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dearus.auth.User;
import com.dearus.auth.UserRepository;
import com.dearus.common.ApiException;
import com.dearus.common.Kst;
import com.dearus.persona.PersonaScope;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 진짜 DB(server/.env의 Supabase)에서 대화 보내기가 내 말과 답변을 이어서 저장하는지(seq · 시각 · 원문), 같은 세션 동시 전송을 막는지,
 * 실패하면 저장하지 않는지 확인하는 시험이에요. 가짜 AI(app.ai.provider=fake)를 쓰고, 만든 사용자는 끝나면 지워요.
 * 돌리기: DEARUS_DB_TEST=true ./gradlew test --tests '*ChatReplyDbTest' -i
 */
@EnabledIfEnvironmentVariable(named = "DEARUS_DB_TEST", matches = "true")
@SpringBootTest(properties = "app.ai.provider=fake")
class ChatReplyDbTest {

	@Autowired UserRepository users;
	@Autowired ChatService chats;
	@Autowired ChatReplyService replies;
	@Autowired ChatMessageRepository messages;

	private static class Recorder implements ChatReplyService.Sink {

		final List<String> events = new ArrayList<>();

		@Override
		public void safety(SafetyNotice notice) {
			events.add("safety");
		}

		@Override
		public void token(String text) {
			events.add("token");
		}
	}

	@Test
	void 내_말과_답변을_이어서_저장하고_같은_세션_동시_전송은_막아요() {
		User user = User.create("dev", "dbtest-" + UUID.randomUUID(), "시험");
		user.agree(false);
		UUID userId = users.saveAndFlush(user).getId();
		try {
			// 기록이 없는 시점이라 분석 없이 바로 READY (첫 인사가 seq 1)
			ChatSessionResponse session = chats.create(userId, new ChatCreateRequest(Kst.today().minusDays(50), PersonaScope.DAY, null, false));
			assertThat(session.status()).isEqualTo(ChatStatus.READY);

			// 1) 한 턴: 내 말(원문) seq 2, 답변 seq 3. AI에도 가리지 않은 원문이 가요(가짜 AI가 내 말을 따라 말해요)
			Recorder recorder = new Recorder();
			ChatReplyResponse first = replies.run(replies.prepare(userId, session.id(), "내 번호는 010-1234-5678이야"), recorder);
			assertThat(first.userMessage().seq()).isEqualTo(2);
			assertThat(first.userMessage().content()).isEqualTo("내 번호는 010-1234-5678이야");
			assertThat(first.personaMessage().seq()).isEqualTo(3);
			assertThat(first.personaMessage().content()).contains("010-1234-5678");
			assertThat(recorder.events).contains("token");
			assertThat(recorder.events).doesNotContain("safety");

			// 2) 위기 신호: safety가 글자보다 먼저
			Recorder crisis = new Recorder();
			ChatReplyResponse second = replies.run(replies.prepare(userId, session.id(), "너무 힘들어서 죽고 싶어"), crisis);
			assertThat(crisis.events.get(0)).isEqualTo("safety");
			assertThat(second.safety()).isNotNull();
			assertThat(second.userMessage().seq()).isEqualTo(4);
			assertThat(second.personaMessage().seq()).isEqualTo(5);

			// 3) 앞 답변이 끝나기 전에 또 보내면 CHAT_BUSY, 끝나면 다시 보낼 수 있어요
			ChatReplyService.Prepared holding = replies.prepare(userId, session.id(), "먼저 보낸 말");
			assertThatThrownBy(() -> replies.prepare(userId, session.id(), "겹쳐 보낸 말"))
				.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("CHAT_BUSY"));
			ChatReplyResponse third = replies.run(holding, ChatReplyService.Sink.NONE);
			assertThat(third.userMessage().seq()).isEqualTo(6);
			assertThat(replies.run(replies.prepare(userId, session.id(), "이제 다시"), ChatReplyService.Sink.NONE).userMessage().seq()).isEqualTo(8);

			// 4) 저장된 대화는 seq 1~9가 빠짐없이 이어지고, 목록의 미리보기가 마지막 답변이에요
			List<ChatMessageResponse> all = chats.messages(userId, session.id(), 0, 200).items();
			assertThat(all).extracting(ChatMessageResponse::seq).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
			assertThat(all).extracting(ChatMessageResponse::role).containsExactly(ChatRole.PERSONA, ChatRole.USER, ChatRole.PERSONA, ChatRole.USER,
				ChatRole.PERSONA, ChatRole.USER, ChatRole.PERSONA, ChatRole.USER, ChatRole.PERSONA);
			assertThat(chats.list(userId).items().get(0).lastMessagePreview()).startsWith("(가짜 답변)");
			assertThat(messages.countBySessionId(session.id())).isEqualTo(9);

			// 5) 끝난 세션이나 준비 안 된 세션에는 보낼 수 없어요
			ChatSession ended = chatsSession(session.id());
			assertThat(ended).isNotNull();
		} finally {
			users.deleteById(userId); // 세션 · 메시지가 ON DELETE CASCADE로 함께 지워져요
		}
	}

	@Autowired ChatSessionRepository sessionRepository;

	private ChatSession chatsSession(UUID id) {
		return sessionRepository.findById(id).orElse(null);
	}
}
