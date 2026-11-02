package com.dearus.common.ai;

import java.util.function.Consumer;

/**
 * 서버가 AI를 부르는 유일한 입구예요. 회사나 모델이 바뀌어도 이 인터페이스를 쓰는 코드는 그대로라서,
 * 구현(`AnthropicAiClient` 등)만 바꾸면 돼요. 호출 전에 동의 확인과 마스킹을 마쳤다는 약속 위에서 불러요.
 */
public interface AiClient {

	/** 실패하면 {@link AiException}을 던져요. 입력 · 응답 글자는 로그에 남기지 않아요. */
	AiResult complete(AiRequest request);

	/**
	 * 답이 만들어지는 대로 글자 조각을 onText로 넘기고, 끝나면 전체 답을 돌려줘요(대화 답변용).
	 * 스트리밍을 지원하지 않는 구현은 한 번에 받아서 한 덩어리로 넘겨요.
	 */
	default AiResult stream(AiRequest request, Consumer<String> onText) {
		AiResult result = complete(request);
		onText.accept(result.text());
		return result;
	}
}
