package com.dearus.chat;

import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 규칙으로 찾는 최소판이에요(자살 · 자해 · "죽고 싶다" 같은 표현). 놓치는 표현이 있고 "죽고 싶을 만큼 웃겨" 같은 농담도 걸려요.
 * 놓치는 쪽이 더 위험해서 애매하면 감지하는 쪽으로 두었고, 표현을 늘리거나 AI 판단을 더하는 건 11월 3주에 다듬어요
 * (DearUs_나중에처리할사항.md). "배고파 죽겠다", "자살골"처럼 흔한 말은 걸리지 않게 했어요.
 */
@Component
public class RuleSafetyDetector implements SafetyDetector {

	private static final Pattern DISTRESS = Pattern.compile(
		"자살(?!골)|자해|극단적\\s?선택|스스로\\s?(?:목숨|생을)|목숨을?\\s?끊|죽고\\s?싶|살기\\s?싫|살고\\s?싶지\\s?않"
			+ "|사라지고\\s?싶|없어지고\\s?싶|뛰어내리고\\s?싶|손목을?\\s?긋");

	@Override
	public boolean detects(String text) {
		return text != null && DISTRESS.matcher(text).find();
	}
}
