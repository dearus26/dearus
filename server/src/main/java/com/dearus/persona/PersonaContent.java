package com.dearus.persona;

import java.util.List;
import tools.jackson.databind.json.JsonMapper;

/**
 * 한 시점의 분석 결과예요(암호화해서 persona_analyses.analysis_enc에 저장해요). 대화용 프롬프트는 이 값으로 대화할 때 만들어요.
 * greetings는 첫 인사 후보 3개이고, 세션을 만들 때 하나를 골라 첫 메시지로 써요(AI를 다시 부르지 않고 바로 READY가 되게요).
 *
 * 2026.11.28(PROMPT_VERSION 6)에 대화가 "그날을 사는 나"답게 구체적인 이야기를 하도록 항목을 더했어요.
 * - day: 하루 단위(DAY)일 때 그날 하루의 흐름. 전후 2주(AROUND)는 비어 있어요
 * - episodes: 이 기간에 있었던 구체적인 일(날짜 · 무슨 일 · 그때 기분)
 * - people: 자주 나오는 사람과 요즘 사이. 실명 대신 관계(친구, 팀원, 엄마)로 적어요
 * - attitude: 대화할 때의 태도(장난스러운지, 투덜대는지, 감정을 드러내는 방식)
 * - hopes: 이 무렵 기대하거나 걱정하던 앞일
 * - coupleGreetings: 커플 모드에서 미래에서 찾아온 연인에게 건네는 첫 인사 3개(greetings는 미래의 나에게 건네는 인사예요)
 * 그 전에 저장한 분석에는 이 항목이 없어서 빈 값으로 읽혀요(버전이 달라 다음 대화 때 다시 만들어져요).
 */
public record PersonaContent(
	String voice,
	List<String> vocabulary,
	String situation,
	String concerns,
	String emotion,
	List<String> samples,
	List<String> greetings,
	String day,
	List<Episode> episodes,
	List<String> people,
	String attitude,
	String hopes,
	List<String> coupleGreetings) {

	/** 이 기간에 있었던 일 하나예요. date는 "2026-09-21"처럼 적고, 모르면 비어 있어요. */
	public record Episode(String date, String what, String feeling) {
	}

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	public PersonaContent {
		vocabulary = vocabulary == null ? List.of() : List.copyOf(vocabulary);
		samples = samples == null ? List.of() : List.copyOf(samples);
		greetings = greetings == null ? List.of() : List.copyOf(greetings);
		episodes = episodes == null ? List.of() : List.copyOf(episodes);
		people = people == null ? List.of() : List.copyOf(people);
		coupleGreetings = coupleGreetings == null ? List.of() : List.copyOf(coupleGreetings);
	}

	/** 2026.11.28 전의 항목만 있는 분석이에요(테스트와 예전 저장 값). 새 항목은 비어 있어요. */
	public PersonaContent(String voice, List<String> vocabulary, String situation, String concerns, String emotion,
		List<String> samples, List<String> greetings) {
		this(voice, vocabulary, situation, concerns, emotion, samples, greetings, null, List.of(), List.of(), null, null, List.of());
	}

	public String toJson() {
		return MAPPER.writeValueAsString(this);
	}

	public static PersonaContent fromJson(String json) {
		return MAPPER.readValue(json, PersonaContent.class);
	}
}
