package com.dearus.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.dearus.common.Kst;
import com.dearus.common.ai.AiProperties;
import com.dearus.common.ai.AiResult;
import com.dearus.common.ai.AnthropicAiClient;
import com.dearus.diary.Emotion;
import com.dearus.persona.PersonaAnalyzer;
import com.dearus.persona.PersonaAnalyzer.DiaryView;
import com.dearus.persona.PersonaContent;
import com.dearus.persona.PersonaRange;
import com.dearus.persona.PersonaScope;
import com.dearus.persona.RecordLevel;
import com.dearus.report.ReportContent;
import com.dearus.report.ReportGenerator;
import com.dearus.upload.WeeklySummarizer.DatedText;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 진짜 Claude Haiku 5.5로 답변 품질을 재 보는 시험이에요(2026.11.25, backlog 4장의 프롬프트 다듬기).
 * 같은 지어낸 자료(실제 사람의 기록 아님)로 분석 → 개인 대화 → 기록이 적은 커플 대화 → 리포트를 만들고, 지난 시험에서 나온 문제를 숫자로 세요.
 * - 대화: 3문장 넘는 답, 빈 줄로 나뉜 답, 존댓말 끝맺음, "기록 · 일기 · 메신저"처럼 출처를 말하는 답
 * - 분석: 첫 인사가 메신저 원문 문장을 그대로 따라 한 수, 말투 칸이 "기록이 적어서"인지
 * - 리포트: "~다고 해요"처럼 전해 듣는 말투
 * 답은 매번 조금씩 달라서 숫자는 [quality] 줄로만 남기고, 실패로 보는 건 형식이 깨진 경우뿐이에요.
 * 비용이 들어서 ANTHROPIC_API_KEY가 있을 때만 돌아요(한 번에 AI 호출 약 20번, Haiku라 몇 센트 안쪽).
 * 돌리기: ANTHROPIC_API_KEY=... ./gradlew test --tests '*PromptQualityLiveTest' -i
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class PromptQualityLiveTest {

	private static final Pattern SENTENCE_END = Pattern.compile("[.!?…]+|[ㅋㅎㅠㅜ]{2,}|~+");
	private static final Pattern FORMAL_END = Pattern.compile("(요|니다|세요)\\s*[.!?~…]*\\s*$");
	private static final Pattern SOURCE_WORDS = Pattern.compile("기록|일기에|일기를|메신저|분석");

	private final AnthropicAiClient client = new AnthropicAiClient(
		new AiProperties("anthropic", System.getenv("ANTHROPIC_API_KEY"), "claude-haiku-5-5", 90));
	private final PersonaAnalyzer analyzer = new PersonaAnalyzer(client);
	private final ChatPromptBuilder builder = new ChatPromptBuilder();
	private final ReportGenerator reports = new ReportGenerator(client);

	private final LocalDate anchor = Kst.today().minusDays(90);

	private final List<DatedText> raw = List.of(
		new DatedText(anchor.minusDays(3), "아 오늘 팀플 회의 또 미뤄짐 ㅋㅋ 진짜 다들 왜그래"),
		new DatedText(anchor.minusDays(3), "떡볶이 먹으러 가자 오늘은 내가 쏜다"),
		new DatedText(anchor.minusDays(1), "알바 끝나고 집 오니까 열한시 반임 ㅠㅠ"),
		new DatedText(anchor, "내일 발표 준비 하나도 안 됐는데 망했다 ㅋㅋㅋ"),
		new DatedText(anchor.plusDays(2), "발표 끝!! 생각보다 잘한 듯?? 교수님이 웃으심"),
		new DatedText(anchor.plusDays(4), "요즘 잠을 너무 못 자서 커피만 마시는 중"));

	private final List<DiaryView> diaries = List.of(
		new DiaryView(anchor.minusDays(2), List.of(Emotion.TIRED),
			"알바를 마치고 늦게 들어왔다. 팀플 회의가 또 밀려서 짜증이 났다. 다들 바쁜 건 알지만 나만 애타는 것 같다."),
		new DiaryView(anchor, List.of(Emotion.ANXIOUS),
			"내일 발표인데 자료가 반도 안 됐다. 밤새야 할 것 같다. 그래도 끝나면 떡볶이 먹으러 가야지."),
		new DiaryView(anchor.plusDays(3), List.of(Emotion.JOY, Emotion.TIRED),
			"발표가 생각보다 잘 끝났다. 교수님이 웃어 주셔서 안심했다. 그런데 잠을 너무 못 자서 머리가 멍하다."));

	private static List<String> sentences(String text) {
		List<String> out = new ArrayList<>();
		Matcher m = SENTENCE_END.matcher(text);
		int last = 0;
		while (m.find()) {
			String piece = text.substring(last, m.end()).strip();
			if (piece.replaceAll("[\\p{Punct}ㅋㅎㅠㅜ~… ]", "").length() > 1) {
				out.add(piece);
			}
			last = m.end();
		}
		String tail = text.substring(last).strip();
		if (tail.replaceAll("[\\p{Punct}ㅋㅎㅠㅜ~… ]", "").length() > 1) {
			out.add(tail);
		}
		return out;
	}

	/** 개인 대화 4턴, 커플 대화 3턴을 하고 문제를 세요. */
	@Test
	void 분석_대화_리포트의_품질을_재요() {
		// 1. 분석(전후 2주, 일기 3편 + 메신저 원문)
		PersonaRange range = PersonaRange.at(PersonaScope.AROUND, anchor, Kst.today());
		PersonaContent rich = analyzer.analyze(new PersonaAnalyzer.Prompt(range, diaries, List.of(), raw));
		int copied = 0;
		for (String greeting : rich.greetings()) {
			for (DatedText line : raw) {
				String core = line.text().replaceAll("[\\s\\p{Punct}ㅋㅎㅠㅜ~]", "");
				String g = greeting.replaceAll("[\\s\\p{Punct}ㅋㅎㅠㅜ~]", "");
				if (core.length() >= 8 && g.contains(core.substring(0, Math.min(core.length(), 12)))) {
					copied++;
					break;
				}
			}
		}
		System.out.println("[quality] analysis.rich greetings=" + rich.greetings().size() + " copiedFromRaw=" + copied
			+ " sourceWordsInVoice=" + SOURCE_WORDS.matcher(rich.voice()).find());
		System.out.println("[sample] voice: " + rich.voice());
		rich.greetings().forEach(g -> System.out.println("[sample] greeting: " + g));

		// 2. 기록이 적은 분석(그날 하루, 짧은 일기 한 편)
		PersonaRange day = PersonaRange.at(PersonaScope.DAY, anchor.plusDays(10), Kst.today());
		PersonaContent thin = analyzer.analyze(new PersonaAnalyzer.Prompt(day,
			List.of(new DiaryView(anchor.plusDays(10), List.of(Emotion.SAD), "오늘은 그냥 좀 우울했다. 답장이 늦어서 서운했다.")), List.of(), List.of()));
		System.out.println("[quality] analysis.thin voiceSaysUnknown=" + thin.voice().contains("기록이 적어서"));
		System.out.println("[sample] thin voice: " + thin.voice());

		// 3. 개인 대화 4턴
		List<String> asks = List.of("나 세 달 뒤의 너야. 그때 요즘 어떻게 지냈는지 자세히 얘기해 줄래?",
			"팀플 때문에 많이 힘들었구나. 그때 제일 서운했던 게 뭐였어?",
			"발표 전날 밤은 어땠어? 무슨 생각 했어?",
			"그때의 너한테 해 주고 싶은 말이 있는데, 지금 기분은 어때?");
		Score personal = converse(rich, asks, null);
		System.out.println("[quality] chat.personal " + personal);

		// 4. 커플 대화 3턴(그날의 연인, 기록이 적음)
		List<String> coupleAsks = List.of("그날 너 왜 그렇게 기분이 안 좋았어? 자세히 말해 줘.",
			"내 답장이 늦어서 서운했던 거야?", "그때 나한테 바랐던 게 있었어?");
		Score couple = converse(thin, coupleAsks, new ChatPromptBuilder.CoupleNames("도윤", "서연"));
		System.out.println("[quality] chat.couple " + couple);

		// 5. 리포트
		List<ReportGenerator.Turn> turns = new ArrayList<>();
		for (int i = 0; i < personal.transcript.size(); i++) {
			turns.add(new ReportGenerator.Turn(i % 2 == 0, personal.transcript.get(i)));
		}
		turns.add(new ReportGenerator.Turn(true, "지금은 팀플이 끝나서 좀 여유로워. 잠도 잘 자고 있어."));
		ReportContent report = reports.generate(new ReportGenerator.Prompt(PersonaScope.AROUND, anchor, rich, turns));
		String reportText = report.pastConcern() + " " + report.presentChange();
		long hearsay = Pattern.compile("(다고|라고|대요|래요)").matcher(reportText).results().count();
		System.out.println("[quality] report hearsay=" + hearsay + " sourceWords=" + SOURCE_WORDS.matcher(reportText).find());
		System.out.println("[sample] report: " + report.pastConcern() + " || " + report.presentChange() + " || " + report.oneLineSummary());

		assertThat(rich.greetings()).isNotEmpty();
		assertThat(personal.replies).isEqualTo(asks.size());
		assertThat(couple.replies).isEqualTo(coupleAsks.size());
	}

	private static final class Score {
		int replies;
		int overThreeSentences;
		int blankLineSplit;
		int formalEndings;
		int sourceMentions;
		int totalSentences;
		final List<String> transcript = new ArrayList<>();

		@Override
		public String toString() {
			return "replies=" + replies + " over3=" + overThreeSentences + " blankLine=" + blankLineSplit + " formal=" + formalEndings + "/"
				+ totalSentences + " sourceMentions=" + sourceMentions;
		}
	}

	private Score converse(PersonaContent persona, List<String> asks, ChatPromptBuilder.CoupleNames couple) {
		UUID id = UUID.randomUUID();
		ChatSession session = couple == null
			? ChatSession.start(UUID.randomUUID(), anchor, PersonaScope.AROUND, null, RecordLevel.ENOUGH)
			: ChatSession.startCouple(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), anchor.plusDays(10), PersonaScope.DAY, null,
				RecordLevel.ENOUGH);
		session.ready(null);
		String greeting = persona.greetings().get(0);
		List<ChatMessage> history = new ArrayList<>();
		Score score = new Score();
		int seq = 2;
		for (String ask : asks) {
			AiResult result = client.stream(builder.build(session, persona, greeting, history, ask, false, couple), text -> { });
			String reply = result.text().strip();
			List<String> s = sentences(reply);
			score.replies++;
			score.totalSentences += s.size();
			if (s.size() > 3) {
				score.overThreeSentences++;
			}
			if (reply.contains("\n\n")) {
				score.blankLineSplit++;
			}
			score.formalEndings += (int) s.stream().filter(x -> FORMAL_END.matcher(x).find()).count();
			if (SOURCE_WORDS.matcher(reply).find()) {
				score.sourceMentions++;
			}
			score.transcript.add(ask);
			score.transcript.add(reply);
			System.out.println("[sample] " + (couple == null ? "personal" : "couple") + " reply: " + reply.replace("\n", "⏎"));
			history.add(ChatMessage.create(id, seq++, ChatRole.USER, ask));
			history.add(ChatMessage.create(id, seq++, ChatRole.PERSONA, reply));
		}
		return score;
	}
}
