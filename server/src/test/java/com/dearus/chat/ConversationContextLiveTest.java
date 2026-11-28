package com.dearus.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.dearus.common.Kst;
import com.dearus.common.Weeks;
import com.dearus.common.ai.AiProperties;
import com.dearus.common.ai.AnthropicAiClient;
import com.dearus.persona.PersonaAnalyzer;
import com.dearus.persona.PersonaContent;
import com.dearus.persona.PersonaRange;
import com.dearus.persona.PersonaScope;
import com.dearus.persona.RecordLevel;
import com.dearus.upload.WeeklySummarizer;
import com.dearus.upload.WeeklySummarizer.DatedText;
import com.dearus.upload.WeeklySummarizer.WeekMessages;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 과거의 나가 맥락 없는 말 조각을 던지고 "무슨 말이야?"에 답하지 못하던 문제(2026.11.28)를 진짜 Claude Haiku 5.5로 재 보는 시험이에요.
 * 지어낸 자료(실제 사람의 기록 아님)로 실제 흐름(요약 → 날마다 한 줄 요약 → 시점 분석 → 대화)을 그대로 돌려요.
 * 1. 개인: 그날 말이 조각난 날에 "오늘 어땠어?" → "그게 무슨 말이야?" → "누가 그랬는데?"
 *    - explained: 되물었을 때 무슨 일(팀플 · 회의 · 미뤄짐)인지 풀어서 말했는지
 *    - inventedName: 기록에 없는 사람 이름을 지어냈는지
 * 2. 커플: 연인이 "아 왜 또 늦어"라고 쓴 날, 말을 거는 사람(나)이 그날 쓴 "늦을 것 같아"를 넣었을 때와 뺐을 때
 *    - knowsLate: 연인 쪽 과거의 나가 서운했던 이유(늦음)를 아는지
 *    - youWereLate: 늦은 사람이 지금 말을 거는 사람이라는 걸 알고 "너 늦었잖아"처럼 말했는지, thirdPerson: "약속 상대"처럼 남 얘기하듯 했는지
 * 답은 매번 달라서 숫자는 [context] 줄로만 남기고, 실패로 보는 건 답이 비었을 때뿐이에요.
 * 비용이 들어서 ANTHROPIC_API_KEY가 있을 때만 돌아요(AI 호출 10번쯤, Haiku라 몇 센트 안쪽).
 * 돌리기: ANTHROPIC_API_KEY=... ./gradlew test --tests '*ConversationContextLiveTest' -i
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class ConversationContextLiveTest {

	private static final Pattern EXPLAINED = Pattern.compile("팀플|회의|미뤄|다음\\s?주|팀원");
	private static final Pattern INVENTED_NAME = Pattern.compile("(민수|지훈|지수|현우|수진|민지|서준|하은)");
	private static final Pattern KNOWS_LATE = Pattern.compile("늦");
	/** 늦은 사람이 지금 말을 거는 사람(서연)이라는 걸 알고 "너"로 말했는지 */
	private static final Pattern KNOWS_IT_WAS_YOU = Pattern.compile("(너|네가|니가)[^.?!]{0,8}늦");
	/** 연인을 남처럼 부른 답 */
	private static final Pattern THIRD_PERSON = Pattern.compile("약속\s?상대|그\s?사람|상대가");

	private final AnthropicAiClient client = new AnthropicAiClient(
		new AiProperties("anthropic", System.getenv("ANTHROPIC_API_KEY"), "claude-haiku-5-5", 90));
	private final ChatPromptBuilder builder = new ChatPromptBuilder();
	private final LocalDate day = Kst.today().minusDays(30);

	/** 실제 흐름처럼 요약(날마다 한 줄 요약 포함) → 하루 단위 분석을 거친 페르소나예요. */
	private PersonaContent analyzeDay(List<DatedText> lines) {
		WeekMessages week = new WeekMessages(Weeks.startOf(day), lines);
		WeeklySummarizer.SummaryParts parts = new WeeklySummarizer(client).summarize(week); // 실제 업로드와 같은 호출이에요
		List<DatedText> dayLines = parts.days().stream().filter(d -> d.line() != null).map(d -> new DatedText(d.date(), d.line())).toList();
		System.out.println("[context-analysis] dayLines: " + dayLines);
		PersonaRange range = PersonaRange.at(PersonaScope.DAY, day, Kst.today());
		PersonaContent content = new PersonaAnalyzer(client).analyze(new PersonaAnalyzer.Prompt(range, List.of(),
			List.of(new PersonaAnalyzer.WeekView(day, day, null, parts.traits(), null)), lines, dayLines));
		System.out.println("[context-analysis] day: " + content.day() + " || episodes: " + content.episodes());
		return content;
	}

	private List<String> converse(PersonaContent persona, List<String> asks, ChatPromptBuilder.CoupleNames couple) {
		ChatSession session = couple == null
			? ChatSession.start(UUID.randomUUID(), day, PersonaScope.DAY, null, RecordLevel.LOW)
			: ChatSession.startCouple(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), day, PersonaScope.DAY, null, RecordLevel.LOW);
		session.ready(null);
		String greeting = couple == null ? persona.greetings().get(0)
			: persona.coupleGreetings().isEmpty() ? "어 왔어?" : persona.coupleGreetings().get(0);
		List<ChatMessage> history = new ArrayList<>();
		List<String> replies = new ArrayList<>();
		UUID id = UUID.randomUUID();
		int seq = 2;
		for (String ask : asks) {
			String reply = client.stream(builder.build(session, persona, greeting, history, ask, false, couple), text -> { }).text().strip();
			assertThat(reply).isNotBlank();
			replies.add(reply);
			history.add(ChatMessage.create(id, seq++, ChatRole.USER, ask));
			history.add(ChatMessage.create(id, seq++, ChatRole.PERSONA, reply));
		}
		return replies;
	}

	@Test
	void 조각난_말을_되물으면_풀어서_설명하고_연인은_내_말로_맥락을_알아요() {
		// 1. 개인: 말이 조각난 하루
		PersonaContent me = analyzeDay(List.of(
			new DatedText(day, "팀플 회의 또 다음주로 미뤄짐"),
			new DatedText(day, "아 진짜 다들 왜그래 ㅋㅋ"),
			new DatedText(day, "나만 진심인듯")));
		List<String> asks = List.of("오늘 어땠어?", "그게 무슨 말이야?", "누가 그랬는데?");
		List<String> replies = converse(me, asks, null);
		for (int i = 0; i < asks.size(); i++) {
			System.out.println("[context-sample] personal Q: " + asks.get(i) + " || A: " + replies.get(i));
		}
		System.out.println("[context] personal explained=" + EXPLAINED.matcher(replies.get(1)).find()
			+ " inventedName=" + replies.stream().anyMatch(r -> INVENTED_NAME.matcher(r).find()));

		// 2. 커플: 연인(도윤)이 서운했던 날. 말을 거는 서연이 그날 쓴 말을 넣을 때와 뺄 때
		PersonaContent partner = analyzeDay(List.of(
			new DatedText(day, "아 왜 또 늦어 ㅠㅠ"),
			new DatedText(day, "알겠어 기다릴게"),
			new DatedText(day, "오늘 좀 서운하다")));
		List<String> coupleAsks = List.of("그날 왜 서운했어?", "그게 무슨 말이야?");
		List<DatedText> mine = List.of(new DatedText(day, "나 오늘 좀 늦을 것 같아 미안"), new DatedText(day, "30분만 기다려 줘"));
		List<String> with = converse(partner, coupleAsks, new ChatPromptBuilder.CoupleNames("도윤", "서연", mine));
		List<String> without = converse(partner, coupleAsks, new ChatPromptBuilder.CoupleNames("도윤", "서연"));
		for (int i = 0; i < coupleAsks.size(); i++) {
			System.out.println("[context-sample] couple+mine Q: " + coupleAsks.get(i) + " || A: " + with.get(i));
			System.out.println("[context-sample] couple-only Q: " + coupleAsks.get(i) + " || A: " + without.get(i));
		}
		System.out.println("[context] couple knowsLate withMine=" + with.stream().anyMatch(r -> KNOWS_LATE.matcher(r).find())
			+ " withoutMine=" + without.stream().anyMatch(r -> KNOWS_LATE.matcher(r).find())
			+ " youWereLate withMine=" + with.stream().anyMatch(r -> KNOWS_IT_WAS_YOU.matcher(r).find())
			+ " withoutMine=" + without.stream().anyMatch(r -> KNOWS_IT_WAS_YOU.matcher(r).find())
			+ " thirdPerson withMine=" + with.stream().filter(r -> THIRD_PERSON.matcher(r).find()).count()
			+ " withoutMine=" + without.stream().filter(r -> THIRD_PERSON.matcher(r).find()).count());
	}
}
