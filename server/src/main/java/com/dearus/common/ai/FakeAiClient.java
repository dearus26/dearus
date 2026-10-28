package com.dearus.common.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 키 없이 개발할 때 쓰는 가짜 AI(AI_PROVIDER=fake). 항상 같은 가짜 요약 JSON을 돌려줘요.
 * 앱 화면 흐름과 서버의 저장 · 상태 전환을 확인하는 용도이고, 배포 서버에서는 켜지 않아요.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "fake")
public class FakeAiClient implements AiClient {

	/**
	 * 메신저 주간 요약(summary · traits · sample)과 시점 분석(voice 등)의 필드를 한 JSON에 모두 담아요.
	 * 각 파서는 자기 필드만 읽어서 어느 호출에 써도 돼요.
	 */
	@Override
	public AiResult complete(AiRequest request) {
		String json = """
			{"summary": "(가짜 요약) 이번 주에는 평범한 일상을 보냈어요.", \
			"traits": "(가짜 분석) 짧은 반말을 쓰고 웃음 표현을 자주 써요.", \
			"sample": ["(가짜 문장) 오늘 뭐 했어?", "(가짜 문장) ㅋㅋ 그러게"], \
			"voice": "(가짜) 짧은 반말을 쓰고 ㅋㅋ를 자주 써요.", \
			"vocabulary": ["ㅋㅋ", "진짜", "아 맞다"], \
			"situation": "(가짜) 학기 중이라 바쁘게 지내요.", \
			"concerns": "(가짜) 시험과 진로가 고민이에요.", \
			"emotion": "(가짜) 피곤하지만 크게 나쁘지는 않아요.", \
			"samples": ["(가짜 문장) 오늘 진짜 피곤했어 ㅋㅋ"], \
			"greetings": ["(가짜 인사) 어 왔어? ㅋㅋ", "(가짜 인사) 오랜만이네", "(가짜 인사) 야 뭐해"]}""";
		return new AiResult(json, 0, 0);
	}
}
