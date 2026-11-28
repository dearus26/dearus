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

		assertThat(system).startsWith("[너는 누구야]\n너는 " + date(end) + " 무렵을 살고 있는 나야.");
		assertThat(system).contains("그 무렵이야").contains("미래의 너 자신");
	}

	@Test
	void 하루_단위는_그날의_나로_말해요() {
		String system = builder.system(session(PersonaScope.DAY), persona, "어 왔어?", false);

		assertThat(system).contains("너는 " + date(anchor) + "을 살고 있는 나야.").contains("오늘 하루를 보내는 중이야")
			.doesNotContain(date(anchor) + " 무렵");
	}

	@Test
	void 상담사가_아니라_그날을_사는_나로_말하라고_해요() {
		String system = builder.system(session(PersonaScope.AROUND), persona, "어 왔어?", false);

		// 2026.11.28: 과거의 나가 미래의 나를 상담해 주는 것처럼 느껴져서 역할과 대화법을 다시 썼어요
		assertThat(system).contains("너는 상담사나 도우미가 아니야").contains("네 이야기가 먼저야")
			.contains("상담사처럼 말하지 마").contains("\"말해 봐, 들어줄게\"").contains("조언을 달라고 하면 정답을 주지 마")
			.contains("받은 질문을 \"너는?\"으로 그대로 되돌리지 마").contains("두세 번에 한 번만")
			.doesNotContain("들어주고 공감해").doesNotContain("짧게 되물어서");
		// 나쁜 답 · 좋은 답 예시가 있어요
		assertThat(system).contains("[예시]").contains("✗ 힘들었겠다. 무슨 일인지 말해 봐, 들어줄게.").contains("○ ");
	}

	@Test
	void 분석_결과가_말투_표현_지금의_너_문장_예시로_들어가요() {
		String system = builder.system(session(PersonaScope.AROUND), persona, "어 왔어?", false);

		assertThat(system).contains("네 말투: 짧은 반말을 쓰고 ㅋㅋ를 자주 써요.");
		assertThat(system).contains("자주 쓰는 표현: 진짜, ㅠㅠ");
		assertThat(system).contains("[지금의 너]\n요즘 생활: 학기 중이라 바빠요.\n요즘 고민: 시험이 고민이에요.\n요즘 기분: 피곤해요.\n");
		assertThat(system).contains("네가 실제로 쓴 말(말투만 따라 하고, 이 문장을 그대로 되풀이하지는 마): \"오늘 진짜 피곤\" \"ㅋㅋ 그러게\"");
		// 아는 일이 있으면 예시의 [ ] 자리에 그 일을 넣어요
		assertThat(system).contains("[ ]는 위에 적힌 네 일로 바꿔 넣는 자리야").contains("[요즘 고민]").doesNotContain("적혀 있지 않아");
	}

	private final PersonaContent dayPersona = new PersonaContent("짧은 반말을 써요.", List.of("ㅋㅋ"), "기록이 적어서 알 수 없어요",
		"기록이 적어서 알 수 없어요", "들떠 있어요.", List.of("짐 싸느라 방 난리남"), List.of("어 왔어?"),
		"아침에 MT 짐을 싸고 저녁에 편의점에 갔어요.",
		List.of(new PersonaContent.Episode("2026-09-21", "MT 짐을 쌌어요.", "설레요."), new PersonaContent.Episode(null, "선배가 장기자랑을 시켰어요.", null)),
		List.of("동아리 선배: 장기자랑을 시켜서 부담스러워요."), "투덜대도 웃어넘겨요.", "MT가 재미있기를 바라요.", List.of("어 왔어? 반갑다"));

	@Test
	void 하루_단위는_그날_하루와_오늘_있었던_일을_날짜_없이_넣어요() {
		String system = builder.system(session(PersonaScope.DAY), dayPersona, "어 왔어?", false);

		assertThat(system).contains("오늘 하루: 아침에 MT 짐을 싸고 저녁에 편의점에 갔어요.\n")
			.contains("오늘 있었던 일:\n- MT 짐을 쌌어요. (기분: 설레요.)\n- 선배가 장기자랑을 시켰어요.\n")
			.contains("주변 사람: 동아리 선배: 장기자랑을 시켜서 부담스러워요.\n")
			.contains("앞으로 기대하거나 걱정하는 것: MT가 재미있기를 바라요.\n")
			.contains("대화할 때 너는: 투덜대도 웃어넘겨요.\n");
		// 생활 · 고민은 몰라도 그날 있었던 일을 알면 예시의 [ ] 자리에 그 일을 넣어요
		assertThat(system).contains("[ ]는 위에 적힌 네 일로 바꿔 넣는 자리야").doesNotContain("적혀 있지 않아")
			.doesNotContain("요즘 생활");
	}

	@Test
	void 전후_2주는_있었던_일에_날짜를_붙이고_그날_하루는_넣지_않아요() {
		String system = builder.system(session(PersonaScope.AROUND), dayPersona, "어 왔어?", false);

		assertThat(system).contains("요즘 있었던 일(네가 직접 겪은 일이라 하나씩 자연스럽게 꺼내도 돼):\n- 9월 21일: MT 짐을 쌌어요.")
			.contains("- 선배가 장기자랑을 시켰어요.").doesNotContain("오늘 하루:");
	}

	@Test
	void 기분만_알고_하는_일을_모르면_하는_일을_지어내지_말라고_해요() {
		PersonaContent moodOnly = new PersonaContent("반말을 써요.", List.of(), "기록이 적어서 알 수 없어요", "기록이 적어서 알 수 없어요",
			"조금 지쳐 보여요.", List.of("집 가고 싶다"), List.of("왔네"));

		String system = builder.system(session(PersonaScope.DAY), moodOnly, "왔네", false);

		// 2026.11.28: 기분만 있을 때도 [ ]에 하는 일을 채우라고 해서 회사 · 회의를 지어냈어요
		assertThat(system).contains("요즘 기분: 조금 지쳐 보여요.").contains("적혀 있지 않아").contains("기분 이야기로만 답해")
			.doesNotContain("[지금 겪는 일]");
	}

	@Test
	void 앞_답이_질문으로_끝났으면_이번_답은_질문하지_말라고_해요() {
		ChatSession session = session(PersonaScope.AROUND);
		List<ChatMessage> askedBack = List.of(message(2, ChatRole.USER, "요즘 어때?"), message(3, ChatRole.PERSONA, "나 팀플 중이야 ㅠㅠ 너는 발표 끝났어? ㅋㅋ"));
		List<ChatMessage> toldStory = List.of(message(2, ChatRole.USER, "요즘 어때?"), message(3, ChatRole.PERSONA, "나 팀플 때문에 정신없어 ㅠㅠ"));

		assertThat(builder.build(session, persona, "어 왔어?", askedBack, "응 끝났어", false).system())
			.endsWith(ChatPromptBuilder.NO_QUESTION_THIS_TURN);
		assertThat(builder.build(session, persona, "어 왔어?", toldStory, "그렇구나", false).system())
			.doesNotContain("[이번 답]");
		// 이력이 없으면 첫 인사가 앞 답이에요
		assertThat(builder.build(session, persona, "어 왔어?", List.of(), "안녕", false).system()).endsWith(ChatPromptBuilder.NO_QUESTION_THIS_TURN);
		assertThat(builder.build(session, persona, "어 왔어 반갑다", List.of(), "안녕", false).system()).doesNotContain("[이번 답]");
		// 위기 신호일 때는 붙이지 않아요(그때는 걱정하며 물어봐도 돼요)
		assertThat(builder.build(session, persona, "어 왔어?", askedBack, "죽고 싶어", true).system()).doesNotContain("[이번 답]");
	}

	@Test
	void 질문으로_끝나는지는_끝의_웃음_표시를_떼고_봐요() {
		assertThat(ChatPromptBuilder.endsWithQuestion("끝났어? ㅋㅋ")).isTrue();
		assertThat(ChatPromptBuilder.endsWithQuestion("진짜?!")).isTrue();
		assertThat(ChatPromptBuilder.endsWithQuestion("왜?? ㅠㅠ~")).isTrue();
		assertThat(ChatPromptBuilder.endsWithQuestion("괜찮아? 나는 피곤해")).isFalse();
		assertThat(ChatPromptBuilder.endsWithQuestion(null)).isFalse();
	}

	@Test
	void 좋은_답_예시는_질문으로_끝나지_않아요() {
		for (PersonaContent content : List.of(persona, new PersonaContent("반말", List.of(), "기록이 적어서 알 수 없어요",
			"기록이 적어서 알 수 없어요", "피곤해요.", List.of("피곤"), List.of("왔네")))) {
			String system = builder.system(session(PersonaScope.DAY), content, "왔네", false);
			assertThat(system.lines().filter(line -> line.startsWith("○ "))).hasSize(3)
				.noneMatch(ChatPromptBuilder::endsWithQuestion);
		}
	}

	@Test
	void 날짜는_월_일로_바꾸고_모양이_다르면_그대로_둬요() {
		assertThat(ChatPromptBuilder.monthDay("2026-09-21")).isEqualTo("9월 21일");
		assertThat(ChatPromptBuilder.monthDay("어제쯤")).isEqualTo("어제쯤");
	}

	@Test
	void 첫_인사는_메시지가_아니라_시스템_프롬프트에_들어가요() {
		String system = builder.system(session(PersonaScope.AROUND), persona, "어 왔어?", false);

		assertThat(system).contains("대화는 네가 먼저 \"어 왔어?\"라고 인사하면서 시작됐어.");
		assertThat(builder.system(session(PersonaScope.AROUND), persona, null, false)).doesNotContain("네가 먼저 \"");
	}

	@Test
	void 규칙은_짧게_모르는_건_지어내지_않기_AI라고_말하지_않기를_담아요() {
		String system = builder.system(session(PersonaScope.AROUND), persona, "어 왔어?", false);

		assertThat(system).contains("말풍선 하나에 1~3문장만").contains("3문장을 넘기지 마").contains("빈 줄이나 줄바꿈 없이")
			.contains("마크다운은 쓰지 마").doesNotContain("가려진").contains("위에 없는 구체적인 일(사람, 사건, 결과)은 지어내지 말고")
			.contains("그 뒤에 일어난 일은 몰라").contains("AI가 아니라 그 무렵의 나").contains("대화 내내 지켜")
			.contains("\"기록\", \"일기\", \"메신저\", \"분석\" 같은 말을 꺼내지 말고")
			.contains("미래의 나가 말한 그 뒤의 일(직장, 사는 곳, 만나는 사람)은 너에게 아직 일어나지 않은 일이야");
		// 답의 모양 규칙은 맨 끝에 둬서 가장 나중에 읽혀요
		assertThat(system.strip()).endsWith("목록이나 마크다운은 쓰지 마.");
	}

	@Test
	void 분석이_근거가_없다고_한_칸은_넣지_않고_말투는_반말로_채워요() {
		PersonaContent thin = new PersonaContent("기록이 적어서 알 수 없어요", List.of(), "기록이 적어서 알 수 없어요",
			"답장이 늦어서 서운했어요.", "기록이 적어서 알 수 없어요", List.of("오늘은 그냥 좀 우울했다."), List.of("왔어?"));

		String system = builder.system(session(PersonaScope.DAY), thin, "왔어?", false);

		assertThat(system).doesNotContain("기록이 적어서").contains("네 말투: 담백한 반말로 자연스럽게 말해.")
			.contains("요즘 고민: 답장이 늦어서 서운했어요.").doesNotContain("요즘 생활").doesNotContain("요즘 기분");
	}

	@Test
	void 분석이_없으면_기본_말투이고_하는_일을_지어내지_말라고_해요() {
		String system = builder.system(session(PersonaScope.DAY), null, "어, 왔어? 오랜만이네.", false);

		assertThat(system).contains("네 말투: 담백한 반말로 자연스럽게 말해.").doesNotContain("기록이 적어서");
		assertThat(system).doesNotContain("요즘 생활").doesNotContain("자주 쓰는 표현");
		// 아는 일이 없으면 하는 일(학교 · 회사)을 짐작하지 말고 기분 이야기만 하게 하고, 예시도 기분 이야기예요(2026.11.28)
		assertThat(system).contains("적혀 있지 않아").contains("회의 · 수업 · 과제처럼 하는 일을 짐작해서 말하지 마")
			.contains("기분 이야기로만 답해").doesNotContain("[요즘 고민]");
	}

	@Test
	void 위기_신호가_있으면_진지하게_걱정하라는_지침이_상담_금지보다_먼저예요() {
		ChatSession session = session(PersonaScope.AROUND);

		assertThat(builder.system(session, persona, "어 왔어?", true)).contains("가볍게 넘기지 말고 진지하게 걱정").contains("109")
			.contains("위의 \"상담사처럼 말하지 마\"보다 이게 먼저야");
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

	@Test
	void 커플_모드는_그날의_연인으로_말하고_말을_거는_사람은_연인이에요() {
		String system = builder.system(session(PersonaScope.DAY), persona, "어 왔어?", false, new ChatPromptBuilder.CoupleNames("도윤", "서연"));

		assertThat(system).startsWith("[너는 누구야]\n너는 " + date(anchor) + "을 살고 있는 도윤야.")
			.contains("너의 연인 서연가 그 뒤의 시간에서 찾아와 말을 걸었어").contains("서연를 위로하거나 문제를 풀어 주려고")
			.contains("그 무렵의 도윤야").contains("같은 말을 꺼내지 말고").contains("누가 잘못했는지 따지지는 마")
			.contains("네 말투: 짧은 반말을 쓰고 ㅋㅋ를 자주 써요.").contains("서연: 요즘 어때?");
		assertThat(system).doesNotContain("미래의 너 자신").doesNotContain("미래의 나:");
	}

	@Test
	void 커플_모드의_이름은_꺾쇠를_바꿔서_넣어요() {
		String system = builder.system(session(PersonaScope.DAY), null, null, false, new ChatPromptBuilder.CoupleNames("<도윤>", "서연"));

		assertThat(system).contains("＜도윤＞").doesNotContain("<도윤>");
	}

	@Test
	void 개인_모드는_이름_없이_미래의_나와_말해요() {
		assertThat(builder.system(session(PersonaScope.DAY), persona, null, false, null))
			.isEqualTo(builder.system(session(PersonaScope.DAY), persona, null, false));
	}
}
