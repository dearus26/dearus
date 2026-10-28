package com.dearus.upload;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 서버의 안전장치 가림. 앱이 폰 안에서 이미 가렸더라도, 놓친 전화번호 · 계좌번호 · 이메일 · 주소 같은 패턴을
 * AI로 보내기 전에 한 번 더 '[전화번호]' 같은 표시로 바꿔요. 이름 · 학교처럼 규칙으로 알 수 없는 것은 앱이 사용자의 확인으로 가려요.
 * 일기 본문도 AI로 보내기 전에 같은 규칙을 써요(저장된 원문은 그대로 둬요).
 * 놓치는 쪽이 새는 쪽이라서, 애매하면 가리는 쪽으로 정했어요.
 */
@Component
public class SensitiveMasker {

	/** text는 가린 결과, count는 가린 건수예요. */
	public record Result(String text, int count) {
	}

	private record Rule(Pattern pattern, String replacement) {
	}

	private static final String BANKS = "국민|신한|우리|하나|농협|기업|카카오뱅크|카뱅|토스뱅크|토스|케이뱅크|새마을|우체국|SC제일|제일|씨티|수협|신협|산업|IBK|KB|NH";
	private static final String PROVINCE = "(?:서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주)(?:특별시|광역시|특별자치시|특별자치도|도)?";

	/** 위에서부터 차례로 적용해요. 앞 규칙이 먼저 가려서 뒤 규칙과 겹치지 않아요. */
	private static final List<Rule> RULES = List.of(
		rule("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[이메일]"),
		rule("https?://\\S+", "[링크]"),
		rule("(?<!\\d)\\d{6}[-\\s]?[1-8]\\d{6}(?!\\d)", "[주민번호]"),
		rule("(?<!\\d)\\d{4}[-\\s]\\d{4}[-\\s]\\d{4}[-\\s]\\d{4}(?!\\d)", "[카드번호]"),
		// 휴대폰: 010-1234-5678 · 01012345678 · +82 10-1234-5678
		rule("(?<!\\d)(?:\\+82[-.\\s]?|0)1[016789][-.\\s]?\\d{3,4}[-.\\s]?\\d{4}(?!\\d)", "[전화번호]"),
		// 지역번호 · 대표번호: 02-123-4567 · 031-123-4567 · 1588-1234 (구분 기호가 있을 때만)
		rule("(?<!\\d)0(?:2|[3-6][1-5]|70|80)[-.\\s]\\d{3,4}[-.\\s]\\d{4}(?!\\d)", "[전화번호]"),
		rule("(?<!\\d)1[5-8]\\d{2}[-.\\s]\\d{4}(?!\\d)", "[전화번호]"),
		// 계좌: 은행 이름 뒤의 숫자 · 하이픈으로 나뉜 긴 숫자 · 11자리 이상 연속 숫자
		rule("(" + BANKS + ")(은행)?\\s*[:：]?\\s*\\d[\\d-]{8,18}\\d", "$1$2 [계좌번호]"),
		rule("(?<!\\d)\\d{3,6}-\\d{2,6}-\\d{3,8}(?:-\\d{1,4})?(?!\\d)", "[계좌번호]"),
		rule("(?<!\\d)\\d{11,16}(?!\\d)", "[계좌번호]"),
		// 주소: 도로명(테헤란로 123 · 세종대로 110 · 123번길 45) · 지번(역삼동 123-4 · 역삼동 12번지) · 동호수(101동 1203호)
		// 번호 뒤에 숫자나 분 · 시간 같은 단위가 오면 '출근길 5분'처럼 주소가 아니라서 빼요.
		rule("(?:" + PROVINCE + "\\s*)?(?:[가-힣]{1,10}[시군구]\\s*)?[가-힣0-9]{2,15}(?:대로|로|길)\\s?\\d{1,4}+(?:-\\d{1,4}+)?(?:번길(?:\\s?\\d{1,4}+)?)?"
			+ "(?:\\s?\\d{1,4}+(?:동|호))?(?!\\d)(?!\\s?(?:분|시간|시|초|개|명|번째|번|년|월|일|주|달|원|살|층|배|%|회|세|잔|마리|장|권|대|곳|군데))", "[주소]"),
		rule("[가-힣]{1,10}(?:동|읍|면|리)\\s?\\d{1,4}(?:-\\d{1,4}|번지)", "[주소]"),
		rule("(?<!\\d)\\d{1,4}동\\s?\\d{1,4}호", "[주소]"));

	private static Rule rule(String regex, String replacement) {
		return new Rule(Pattern.compile(regex), replacement);
	}

	public Result mask(String text) {
		String current = text;
		int total = 0;
		for (Rule rule : RULES) {
			Matcher matcher = rule.pattern().matcher(current);
			StringBuilder out = new StringBuilder();
			int count = 0;
			while (matcher.find()) {
				matcher.appendReplacement(out, rule.replacement());
				count++;
			}
			if (count > 0) {
				matcher.appendTail(out);
				current = out.toString();
				total += count;
			}
		}
		return new Result(current, total);
	}
}
