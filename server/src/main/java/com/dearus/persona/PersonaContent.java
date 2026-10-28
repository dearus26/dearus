package com.dearus.persona;

import java.util.List;
import tools.jackson.databind.json.JsonMapper;

/**
 * 한 시점의 분석 결과예요(암호화해서 persona_analyses.analysis_enc에 저장해요). 대화용 프롬프트는 이 값으로 대화할 때 만들어요.
 * greetings는 첫 인사 후보 3개이고, 세션을 만들 때 하나를 골라 첫 메시지로 써요(AI를 다시 부르지 않고 바로 READY가 되게요).
 */
public record PersonaContent(
	String voice,
	List<String> vocabulary,
	String situation,
	String concerns,
	String emotion,
	List<String> samples,
	List<String> greetings) {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	public String toJson() {
		return MAPPER.writeValueAsString(this);
	}

	public static PersonaContent fromJson(String json) {
		return MAPPER.readValue(json, PersonaContent.class);
	}
}
