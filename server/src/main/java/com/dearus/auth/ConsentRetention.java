package com.dearus.auth;

import java.time.Duration;

/**
 * AI 분석 동의를 철회한 뒤 저장된 분석 데이터(메신저 요약 · 시점 분석)와 대화 기록을 얼마나 보관하는지예요(2026.10.29 결정).
 * 철회한 사람이 마음이 바뀌어 다시 동의하면 이어서 쓸 수 있게 30일 동안 두었다가 지워요. AI를 부르는 기능은 철회하는 순간 막혀요.
 * 일기는 AI 데이터가 아니라서 지우지 않아요. AI 회사(Anthropic)에 이미 보낸 글자도 보낸 뒤 30일 안에 지워져서 두 기간이 맞아요.
 */
public final class ConsentRetention {

	public static final Duration AFTER_REVOKE = Duration.ofDays(30);

	private ConsentRetention() {
	}
}
