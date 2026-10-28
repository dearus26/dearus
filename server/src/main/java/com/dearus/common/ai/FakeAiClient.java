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

	@Override
	public AiResult complete(AiRequest request) {
		String json = """
			{"summary": "(가짜 요약) 이번 주에는 평범한 일상을 보냈어요.", \
			"traits": "(가짜 분석) 짧은 반말을 쓰고 웃음 표현을 자주 써요.", \
			"sample": ["(가짜 문장) 오늘 뭐 했어?", "(가짜 문장) ㅋㅋ 그러게"]}""";
		return new AiResult(json, 0, 0);
	}
}
