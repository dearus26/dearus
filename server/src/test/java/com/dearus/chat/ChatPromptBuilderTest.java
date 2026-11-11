package com.dearus.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.dearus.common.Kst;
import com.dearus.common.ai.AiEffort;
import com.dearus.common.ai.AiMessage;
import com.dearus.common.ai.AiRequest;
import com.dearus.persona.PersonaContent;
import com.dearus.persona.PersonaRange;
import com.dearus.persona.PersonaScope;
import com.dearus.persona.RecordLevel;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 대화 프롬프트(시스템 프롬프트 · 이력 자르기 · 원문 그대로 보내기 · 번갈아 보내기)를 AI 호출 없이 확인해요. */
class ChatPromptBuilderTest {

	private final ChatPromptBuilder builder = new ChatPromptBuilder();
	private final UUID userId = UUID.randomUUID();
	private final UUID sessionId = UUID.randomUUID();
	private final LocalDate anchor = Kst.today().minusDays(60);
	private final PersonaContent persona = new PersonaContent("짧은 반말을 쓰고 ㅋㅋ를 자주 써요.", List.of("진짜", "ㅠㅠ"), "학기 중이라 바빠요.",
		"시험이 고민이에요.", "피곤해요.", List.of("오늘 진짜 피곤", "ㅋㅋ 그러게"), List.of("어 왔어?", "오랜만"));

	private ChatSession session(PersonaScope scope) {
		ChatSession session = ChatSession.start(userId, anchor, scope, null, RecordLevel.ENOUGH);
		session.ready(null);
		return session;
	}

	private ChatMessage message(int seq, ChatRole role, String content) {
		return ChatMessage.create(sessionId, seq, role, content);
	}

	private static String date(LocalDate date) {
		return DateTimeFormatter.ofPattern("yyyy년 M월 d일").format(date);
	}

	// ───────── 시스템 프롬프트 ─────────

	@Test
	void 전후_2주는_구간의_끝_날짜_무렵의_나로_말해요() {
		ChatSession session = session(PersonaScope.AROUND);
		LocalDate end = PersonaRange.at(PersonaScope.AROUND, anchor, Kst.today()).end();

		String system = builder.system(session, persona, "어 왔어?", false);

		assertThat(system).contains("지금은 " + date(end) + " 무렵이고");
		assertThat(system).contains("미래의 너 자신");
	}

	@Test
	void 하루_단위는_그날의_나로_말해요() {
		String system = builder.system(session(PersonaScope.DAY), persona, "어 왔어?", false);

		assertThat(system).contains("지금은 " + date(anchor) + "이고").doesNotContain(date(anchor) + " 무렵");
	}

	@Test
	void 분석_결과가_말투_표현_상황_고민_감정_문장_예시로_들어가요() {
		String system = builder.system(session(PersonaScope.AROUND), persona, "어 왔어?", false);

		assertThat(system).contains("네 말투: 짧은 반말을 쓰고 ㅋㅋ를 자주 써요.");
		assertThat(system).contains("자주 쓰는 표현: 진짜, ㅠㅠ");
		assertThat(system).contains("이 무렵의 생활: 학기 중이라 바빠요.");
		assertThat(system).contains("이 무렵의 고민: 시험이 고민이에요.");
		assertThat(system).contains("이 무렵의 감정: 피곤해요.");
		assertThat(system).contains("네가 실제로 쓴 문장 예시: \"오늘 진짜 피곤\" \"ㅋㅋ 그러게\"");
	}

	@Test
	void 첫_인사는_메시지가_아니라_시스템_프롬프트에_들어가요() {
		String system = builder.system(session(PersonaScope.AROUND), persona, "어 왔어?", false);

		assertThat(system).contains("대화는 네가 먼저 \"어 왔어?\"라고 인사하면서 시작됐어.");
		assertThat(builder.system(session(PersonaScope.AROUND), persona, null, false)).doesNotContain("먼저");
	}

	@Test
	void 규칙은_짧게_모르는_건_지어내지_않기_AI라고_말하지_않기를_담아요() {
		String system = builder.system(session(PersonaScope.AROUND), persona, "어 왔어?", false);

		assertThat(system).contains("1~3문장으로 짧게").contains("줄을 나눠 문단을 만들지 말고").contains("마크다운은 쓰지 마")
			.doesNotContain("가려진").contains("기록에 없는 일은 지어내지 말고")
			.contains("그 뒤에 일어난 일은 몰라").contains("AI가 아니라 그 무렵의 나").contains("대화 내내 지켜");
	}

	@Test
	void 분석이_없으면_기본_말투예요() {
		String system = builder.system(session(PersonaScope.DAY), null, "어, 왔어? 오랜만이네.", false);

		assertThat(system).contains("남은 기록이 없어서 네 말투를 잘 몰라").contains("담백한 반말");
		assertThat(system).doesNotContain("네 말투:").doesNotContain("이 무렵의 생활");
	}

	@Test
	void 위기_신호가_있으면_진지하게_걱정하라는_지침이_더해져요() {
		ChatSession session = session(PersonaScope.AROUND);

		assertThat(builder.system(session, persona, "어 왔어?", true)).contains("가볍게 넘기지 말고 진지하게 걱정").contains("109");
		assertThat(builder.system(session, persona, "어 왔어?", false)).doesNotContain("진지하게 걱정").doesNotContain("109");
	}

	// ───────── 메시지 ─────────

	@Test
	void 이력이_없으면_방금_한_말_하나만_보내요() {
		List<AiMessage> messages = builder.messages(List.of(), "안녕");

		assertThat(messages).containsExactly(new AiMessage(AiMessage.Role.USER, "안녕"));
	}

	@Test
	void user와_assistant가_번갈아_가고_마지막은_방금_한_말이에요() {
		List<ChatMessage> history = List.of(message(2, ChatRole.USER, "응 나 미래의 너야"), message(3, ChatRole.PERSONA, "헐 진짜?"),
			message(4, ChatRole.USER, "시험 잘 봤어"), message(5, ChatRole.PERSONA, "오 대박"));

		List<AiMessage> messages = builder.messages(history, "고마워");

		assertThat(messages).extracting(AiMessage::role).containsExactly(AiMessage.Role.USER, AiMessage.Role.ASSISTANT,
			AiMessage.Role.USER, AiMessage.Role.ASSISTANT, AiMessage.Role.USER);
		assertThat(messages).extracting(AiMessage::text).containsExactly("응 나 미래의 너야", "헐 진짜?", "시험 잘 봤어", "오 대박", "고마워");
	}

	@Test
	void 내가_친_말도_페르소나의_말도_가리지_않고_그대로_보내요() {
		List<ChatMessage> history = List.of(message(2, ChatRole.USER, "내 번호는 010-1234-5678이야"), message(3, ChatRole.PERSONA, "연락처는 괜찮아 ㅋㅋ"));

		List<AiMessage> messages = builder.messages(history, "메일은 a@b.com 이야");

		assertThat(messages.get(0).text()).isEqualTo("내 번호는 010-1234-5678이야");
		assertThat(messages.get(1).text()).isEqualTo("연락처는 괜찮아 ㅋㅋ");
		assertThat(messages.get(2).text()).isEqualTo("메일은 a@b.com 이야");
	}

	@Test
	void 이력이_너무_길면_최근_것만_보내고_항상_user로_시작해요() {
		List<ChatMessage> history = new ArrayList<>();
		for (int seq = 2; seq <= 101; seq++) { // 100개: user, persona 번갈아
			history.add(message(seq, seq % 2 == 0 ? ChatRole.USER : ChatRole.PERSONA, "말 " + seq));
		}

		List<AiMessage> messages = builder.messages(history, "마지막 말");

		assertThat(messages.size()).isLessThanOrEqualTo(ChatPromptBuilder.MAX_HISTORY_MESSAGES + 1);
		assertThat(messages.get(0).role()).isEqualTo(AiMessage.Role.USER);
		assertThat(messages.get(messages.size() - 1)).isEqualTo(new AiMessage(AiMessage.Role.USER, "마지막 말"));
		assertThat(messages.get(messages.size() - 2).text()).isEqualTo("말 101"); // 가장 최근 이력이 남아 있어요
		for (int i = 1; i < messages.size(); i++) {
			assertThat(messages.get(i).role()).isNotEqualTo(messages.get(i - 1).role());
		}
	}

	@Test
	void 자르다_페르소나의_말로_시작하면_user가_나올_때까지_떼어요() {
		List<ChatMessage> history = new ArrayList<>();
		for (int seq = 2; seq <= 2 + ChatPromptBuilder.MAX_HISTORY_MESSAGES; seq++) { // 41개: user로 시작해서 user로 끝나, 자르면 persona로 시작해요
			history.add(message(seq, (seq - 2) % 2 == 0 ? ChatRole.USER : ChatRole.PERSONA, "말 " + seq));
		}

		List<AiMessage> messages = builder.messages(history, "새 말");

		assertThat(messages.get(0).role()).isEqualTo(AiMessage.Role.USER);
	}

	@Test
	void 같은_역할이_이어진_저장_데이터는_한_메시지로_합쳐요() {
		List<ChatMessage> history = List.of(message(2, ChatRole.USER, "첫 말"), message(3, ChatRole.USER, "이어서"), message(4, ChatRole.PERSONA, "응"));

		List<AiMessage> messages = builder.messages(history, "그리고");

		assertThat(messages).extracting(AiMessage::role).containsExactly(AiMessage.Role.USER, AiMessage.Role.ASSISTANT, AiMessage.Role.USER);
		assertThat(messages.get(0).text()).isEqualTo("첫 말\n이어서");
	}

	@Test
	void 요청은_effort_LOW와_넉넉한_max_tokens로_만들어요() {
		AiRequest request = builder.build(session(PersonaScope.AROUND), persona, "어 왔어?", List.of(), "안녕", false);

		assertThat(request.effort()).isEqualTo(AiEffort.LOW);
		assertThat(request.maxTokens()).isEqualTo(1024);
		assertThat(request.messages()).hasSize(1);
		assertThat(request.system()).contains("미래의 너 자신");
	}
}
