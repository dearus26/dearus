package com.dearus.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 비용 · 남용을 막는 상한이에요(app.limits, 2026.11.25 결정). 하루는 한국 시간 자정 기준이에요.
 *
 * @param dailyChatMessages 한 사람이 하루에 보낼 수 있는 대화 메시지(내 말) 수
 * @param chatMessagesPerSession 대화 하나에서 보낼 수 있는 내 말 수
 * @param dailyMessengerUploads 한 사람이 하루에 올릴 수 있는 메신저 업로드 수
 * @param dailyReports 한 사람이 하루에 만들 수 있는 리포트 수
 * @param loginPerMinute 로그인(POST /auth/*)을 같은 IP에서 1분에 부를 수 있는 수
 * @param requestsPerMinute 로그인한 사람이 1분에 부를 수 있는 API 수
 */
@ConfigurationProperties("app.limits")
public record LimitProperties(
	int dailyChatMessages,
	int chatMessagesPerSession,
	int dailyMessengerUploads,
	int dailyReports,
	int loginPerMinute,
	int requestsPerMinute) {

	public LimitProperties {
		if (dailyChatMessages < 1 || chatMessagesPerSession < 1 || dailyMessengerUploads < 1 || dailyReports < 1
			|| loginPerMinute < 1 || requestsPerMinute < 1) {
			throw new IllegalStateException("app.limits의 값은 모두 1 이상이어야 해요");
		}
	}
}
