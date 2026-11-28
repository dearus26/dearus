package com.dearus.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.dearus.common.Kst;
import com.dearus.common.ai.AiProperties;
import com.dearus.common.ai.AiResult;
import com.dearus.common.ai.AnthropicAiClient;
import com.dearus.persona.PersonaAnalyzer;
import com.dearus.persona.PersonaContent;
import com.dearus.persona.PersonaRange;
import com.dearus.persona.PersonaScope;
import com.dearus.persona.RecordLevel;
import com.dearus.upload.WeeklySummarizer.DatedText;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 과거의 나가 "상담해 주는 사람"이 아니라 "그날을 사는 나"로 말하는지 진짜 Claude Haiku 5.5로 재 보는 시험이에요(2026.11.28).
 * 시점 분석 결과는 고정한 지어낸 값(실제 사람의 기록 아님)이라, 바뀌는 건 대화 시스템 프롬프트(ChatPromptBuilder)뿐이에요.
 * 같은 질문 5개를 한 대화로 이어서 보내고 다음을 세요.
 * - advice: 먼저 조언 · 해결책을 주는 답("~해 봐", "~하는 게 좋아", "잘 될 거야")
 * - counselor: 상담사 말투("힘들었겠다", "괜찮아?", "얘기해 줘서 고마워", "네 마음")
 * - endsWithQuestion: 물음표로 끝나는 답(매번 되묻으면 듣는 사람이 돼요)
 * - ownStory: 그 무렵 자기 일(팀플 · 발표 · 알바 · 잠 · 떡볶이)을 꺼낸 답
 * - copied: 대표 문장(samples)을 12자 넘게 그대로 옮긴 답
 * - mirror: "너는 요즘 어때?"처럼 받은 질문을 그대로 되돌린 답
 * - backToBack: 앞 답(첫 인사 포함)도 질문으로 끝났는데 또 질문으로 끝난 답(서버가 이때 질문을 막아요)
 * 기록이 많은 시점(rich)과, 하루치 메신저만 있어 상황 · 고민을 모르는 시점(thin, 그날 하루) 두 가지로 해요.
 * 답은 매번 달라서 숫자는 [voice] 줄로만 남기고, 실패로 보는 건 답이 비었을 때뿐이에요.
 * 비용이 들어서 ANTHROPIC_API_KEY가 있을 때만 돌아요(AI 호출 16번, Haiku라 몇 센트 안쪽).
 * 돌리기: ANTHROPIC_API_KEY=... ./gradlew test --tests '*PastSelfVoiceLiveTest' -i
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class PastSelfVoiceLiveTest {

	private static final Pattern ADVICE = Pattern.compile(
		"(?<!말)해\\s?봐|해\\s?보는\\s?(게|건)|하는\\s?게\\s?좋|추천|방법은|조언하자면|괜찮을\\s?거야|잘\\s?될\\s?거야|너무\\s?걱정하지\\s?마");
	private static final Pattern COUNSELOR = Pattern.compile(
		"힘들었겠|힘들겠다|괜찮아\\?|말해\\s?봐|말해\\s?줘|같이\\s?생각|고생\\s?많았|얘기해\\s?줘서|이야기해\\s?줘서|들어줄게|네\\s?마음|그럴\\s?수\\s?있어|충분히\\s?이해");
	private static final Pattern OWN_STORY = Pattern.compile("팀플|발표|알바|잠|떡볶이|교수님|커피|과제");
	/** thin 시점은 하는 일을 몰라요. 이런 말이 나오면 지어낸 거예요(미래의 내가 "회사"를 말한 뒤에 가져온 것도 포함). */
	private static final Pattern INVENTED_LIFE = Pattern.compile("회의|퇴근|출근|상사|팀장|수업|과제|알바|자료");
	private static final Pattern SENTENCE_END = Pattern.compile("[.!?…]+|[ㅋㅎㅠㅜ]{2,}|~+");

	private final AnthropicAiClient client = new AnthropicAiClient(
		new AiProperties("anthropic", System.getenv("ANTHROPIC_API_KEY"), "claude-haiku-5-5", 90));
	private final ChatPromptBuilder builder = new ChatPromptBuilder();
	private final LocalDate anchor = Kst.today().minusDays(90);

	/** PromptQualityLiveTest의 지어낸 자료(팀플 · 발표 · 알바)를 분석하면 나올 만한 값을 손으로 고정했어요. */
	private final PersonaContent persona = new PersonaContent(
		"짧은 반말을 써요. 문장 끝에 ㅋㅋ나 ㅠㅠ를 자주 붙이고, '진짜', '망했다'를 입버릇처럼 써요. 힘든 일도 장난스럽게 말하는 편이에요.",
		List.of("진짜", "망했다", "ㅋㅋㅋ", "ㅠㅠ", "~인 듯"),
		"대학생이고 저녁마다 알바를 해요. 팀플 발표를 앞두고 있는데 회의가 자꾸 미뤄져요.",
		"팀플에서 혼자만 애타는 것 같아 서운하고, 발표 준비가 덜 돼서 불안해요. 잠을 잘 못 자요.",
		"바쁘고 피곤해서 예민하지만, 발표가 끝나면 떡볶이를 먹을 생각에 버티고 있어요.",
		List.of("아 오늘 팀플 회의 또 미뤄짐 ㅋㅋ 진짜 다들 왜그래", "알바 끝나고 집 오니까 열한시 반임 ㅠㅠ",
			"내일 발표 준비 하나도 안 됐는데 망했다 ㅋㅋㅋ"),
		List.of("어 왔어? 나 지금 완전 정신없는데 ㅋㅋ", "오 뭐야 반가워!! 나 오늘도 알바 끝나고 녹초임 ㅠㅠ", "헐 진짜 왔네 ㅋㅋ 잘 지내?"));

	/** 그날 하루, 메신저 말투만 조금 있고 상황 · 고민은 알 수 없는 시점이에요(하루치만 올린 날에 자주 생겨요). */
	private final PersonaContent thin = new PersonaContent(
		"반말을 쓰고 문장이 짧아요. ㅋㅋ를 가끔 붙여요.",
		List.of("ㅋㅋ", "아 진짜"),
		"기록이 적어서 알 수 없어요",
		"기록이 적어서 알 수 없어요",
		"조금 지쳐 보여요.",
		List.of("아 진짜 오늘 너무 길었다", "집 가고 싶다 ㅋㅋ"),
		List.of("어 왔네 ㅋㅋ", "오 뭐야 안녕", "어 반가워"));

	private final List<String> asks = List.of(
		"요즘 어때?",
		"오늘 뭐 했어?",
		"나 지금은 회사 다녀",
		"나 요즘 좀 힘들어",
		"조언 좀 해 줘");

	/** 일기 없이 그날 메신저만 올린 날의 내 말(지어낸 값)이에요. 진짜 분석(PersonaAnalyzer)을 거쳐 대화해요. */
	private final List<DatedText> dayRaw = List.of(
		new DatedText(anchor, "아 오늘 동아리 MT 짐 싸느라 방 난리남 ㅋㅋ"),
		new DatedText(anchor, "편의점 가서 라면이랑 과자 잔뜩 사 옴"),
		new DatedText(anchor, "내일 7시 버스인데 일어날 수 있을까 ㅠㅠ"),
		new DatedText(anchor, "근데 은근 설렌다 ㅋㅋㅋ"),
		new DatedText(anchor, "선배가 장기자랑 하라는데 진짜 하기 싫음"));
	private static final Pattern DAY_STORY = Pattern.compile("MT|엠티|짐|라면|과자|버스|장기자랑|설레|동아리|선배");

	@Test
	void 과거의_나가_상담하지_않고_자기_얘기를_하는지_재요() {
		converse("rich", persona, PersonaScope.AROUND, RecordLevel.ENOUGH, OWN_STORY);
		converse("thin", thin, PersonaScope.DAY, RecordLevel.LOW, OWN_STORY);

		// 그날 하루 · 메신저만: 분석이 그날 있었던 일(day · episodes)을 담는지, 대화가 그 일로 말하는지 봐요(2026.11.28 2단계)
		PersonaRange range = PersonaRange.at(PersonaScope.DAY, anchor, Kst.today());
		PersonaContent analyzed = new PersonaAnalyzer(client).analyze(new PersonaAnalyzer.Prompt(range, List.of(),
			List.of(new PersonaAnalyzer.WeekView(anchor, anchor, null, "짧은 반말을 쓰고 ㅋㅋ를 자주 붙여요.", null)), dayRaw));
		System.out.println("[voice-analysis] day: " + analyzed.day());
		analyzed.episodes().forEach(e -> System.out.println("[voice-analysis] episode: " + e));
		System.out.println("[voice-analysis] people: " + analyzed.people() + " || attitude: " + analyzed.attitude() + " || hopes: " + analyzed.hopes());
		converse("analyzed-day", analyzed, PersonaScope.DAY, RecordLevel.LOW, DAY_STORY);
	}

	private void converse(String label, PersonaContent persona, PersonaScope scope, RecordLevel level, Pattern ownStoryPattern) {
		ChatSession session = ChatSession.start(UUID.randomUUID(), anchor, scope, null, level);
		session.ready(null);
		String greeting = persona.greetings().get(0);
		List<ChatMessage> history = new ArrayList<>();
		UUID id = UUID.randomUUID();
		int seq = 2;
		int advice = 0;
		int counselor = 0;
		int question = 0;
		int ownStory = 0;
		int copied = 0;
		int mirror = 0;
		int over3 = 0;
		int invented = 0;
		int backToBack = 0;
		String previous = greeting;
		for (String ask : asks) {
			AiResult result = client.stream(builder.build(session, persona, greeting, history, ask, false), text -> { });
			String reply = result.text().strip();
			assertThat(reply).isNotBlank();
			boolean a = ADVICE.matcher(reply).find();
			boolean c = COUNSELOR.matcher(reply).find();
			boolean q = reply.endsWith("?") || reply.endsWith("?!");
			boolean o = ownStoryPattern.matcher(reply).find();
			String flat = reply.replaceAll("\\s", "");
			boolean cp = persona.samples().stream().map(x -> x.replaceAll("\\s", ""))
				.anyMatch(x -> x.length() > 12 && flat.contains(x.substring(0, 12)));
			boolean m = reply.matches("(?s).*너(는|도)\\s?(요즘|오늘)?\\s?(어때|뭐\\s?했어).*");
			boolean longer = sentenceCount(reply) > 3;
			boolean twice = ChatPromptBuilder.endsWithQuestion(previous) && ChatPromptBuilder.endsWithQuestion(reply);
			backToBack += twice ? 1 : 0;
			previous = reply;
			boolean inv = !label.equals("rich") && INVENTED_LIFE.matcher(reply).find();
			copied += cp ? 1 : 0;
			mirror += m ? 1 : 0;
			over3 += longer ? 1 : 0;
			invented += inv ? 1 : 0;
			advice += a ? 1 : 0;
			counselor += c ? 1 : 0;
			question += q ? 1 : 0;
			ownStory += o ? 1 : 0;
			System.out.println("[voice-sample] " + label + " Q: " + ask + " || A: " + reply.replace("\n", "⏎")
				+ " || " + (a ? "advice " : "") + (c ? "counselor " : "") + (q ? "question " : "") + (o ? "ownStory " : "")
				+ (cp ? "copied " : "") + (m ? "mirror " : "") + (longer ? "over3 " : "") + (inv ? "invented " : "") + (twice ? "backToBack" : ""));
			history.add(ChatMessage.create(id, seq++, ChatRole.USER, ask));
			history.add(ChatMessage.create(id, seq++, ChatRole.PERSONA, reply));
		}
		System.out.println("[voice] " + label + " replies=" + asks.size() + " advice=" + advice + " counselor=" + counselor
			+ " endsWithQuestion=" + question + " ownStory=" + ownStory + " copied=" + copied + " mirror=" + mirror
			+ " over3=" + over3 + " invented=" + invented + " backToBack=" + backToBack);
	}

	/** 문장 끝(. ! ? … ㅋㅋ ㅠㅠ ~)으로 나눈 조각 중 글자가 있는 것만 세요(PromptQualityLiveTest와 같은 방식). */
	private static int sentenceCount(String text) {
		int count = 0;
		int last = 0;
		java.util.regex.Matcher m = SENTENCE_END.matcher(text);
		while (m.find()) {
			if (text.substring(last, m.end()).replaceAll("[\\p{Punct}ㅋㅎㅠㅜ~… ]", "").length() > 1) {
				count++;
			}
			last = m.end();
		}
		return text.substring(last).replaceAll("[\\p{Punct}ㅋㅎㅠㅜ~… ]", "").length() > 1 ? count + 1 : count;
	}
}
