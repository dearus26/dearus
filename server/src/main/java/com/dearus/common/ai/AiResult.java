package com.dearus.common.ai;

/** AI의 답. 토큰 수는 비용을 재는 데만 써요. */
public record AiResult(String text, long inputTokens, long outputTokens) {
}
