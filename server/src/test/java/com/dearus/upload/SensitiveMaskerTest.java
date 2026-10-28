package com.dearus.upload;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 서버의 안전장치 가림 규칙을 확인해요. 새는 것(가려야 하는데 안 가림)과 과하게 가리는 것을 모두 봐요. */
class SensitiveMaskerTest {

	private final SensitiveMasker masker = new SensitiveMasker();

	private String masked(String input) {
		return masker.mask(input).text();
	}

	@Test
	void 휴대폰_번호는_모양이_달라도_가려요() {
		assertThat(masked("내 번호 010-1234-5678 이야")).isEqualTo("내 번호 [전화번호] 이야");
		assertThat(masked("01012345678로 연락줘")).isEqualTo("[전화번호]로 연락줘");
		assertThat(masked("010 1234 5678")).isEqualTo("[전화번호]");
		assertThat(masked("+82 10-1234-5678")).isEqualTo("[전화번호]");
		assertThat(masked("011.123.4567")).isEqualTo("[전화번호]");
	}

	@Test
	void 지역번호와_대표번호도_가려요() {
		assertThat(masked("집 02-123-4567")).isEqualTo("집 [전화번호]");
		assertThat(masked("031-1234-5678")).isEqualTo("[전화번호]");
		assertThat(masked("고객센터 1588-1234")).isEqualTo("고객센터 [전화번호]");
	}

	@Test
	void 이메일과_링크를_가려요() {
		assertThat(masked("seoyeon.kim+dev@example.co.kr 로 보내줘")).isEqualTo("[이메일] 로 보내줘");
		assertThat(masked("이거 봐 https://www.example.com/a?b=1 재밌어")).isEqualTo("이거 봐 [링크] 재밌어");
	}

	@Test
	void 주민번호와_카드번호를_가려요() {
		assertThat(masked("990101-1234567")).isEqualTo("[주민번호]");
		assertThat(masked("9901011234567")).isEqualTo("[주민번호]");
		assertThat(masked("1234-5678-9012-3456")).isEqualTo("[카드번호]");
	}

	@Test
	void 계좌번호는_은행_이름_하이픈_긴_숫자로_가려요() {
		assertThat(masked("국민 123456-01-123456 로 보내")).isEqualTo("국민 [계좌번호] 로 보내");
		assertThat(masked("신한은행 110123456789")).isEqualTo("신한은행 [계좌번호]");
		assertThat(masked("카카오뱅크 3333-01-1234567")).isEqualTo("카카오뱅크 [계좌번호]");
		assertThat(masked("110-123-456789")).isEqualTo("[계좌번호]");
		assertThat(masked("12345678901234")).isEqualTo("[계좌번호]");
	}

	@Test
	void 주소를_가려요() {
		assertThat(masked("서울 강남구 테헤란로 123 에서 만나")).isEqualTo("[주소] 에서 만나");
		assertThat(masked("세종대로 110")).isEqualTo("[주소]");
		assertThat(masked("테헤란로 123번길 45")).isEqualTo("[주소]");
		assertThat(masked("역삼동 123-4")).isEqualTo("[주소]");
		assertThat(masked("역삼동 12번지")).isEqualTo("[주소]");
		assertThat(masked("101동 1203호")).isEqualTo("[주소]");
		assertThat(masked("테헤란로 123에서 봤어")).isEqualTo("[주소]에서 봤어");
	}

	@Test
	void 주소가_아닌_흔한_말은_가리지_않아요() {
		assertThat(masked("출근길 5분 걸렸어")).isEqualTo("출근길 5분 걸렸어");
		assertThat(masked("퇴근길 10분")).isEqualTo("퇴근길 10분");
		assertThat(masked("집으로 3시에 갈게")).isEqualTo("집으로 3시에 갈게");
		assertThat(masked("행동 3번 했어")).isEqualTo("행동 3번 했어");
		assertThat(masked("이길 3번 했어")).isEqualTo("이길 3번 했어");
	}

	@Test
	void 날짜와_시간과_짧은_숫자는_가리지_않아요() {
		assertThat(masked("2026-10-28 에 보자")).isEqualTo("2026-10-28 에 보자");
		assertThat(masked("10-12-2026")).isEqualTo("10-12-2026");
		assertThat(masked("오늘 3시 30분에 7번 버스")).isEqualTo("오늘 3시 30분에 7번 버스");
		assertThat(masked("12000원이야")).isEqualTo("12000원이야");
	}

	@Test
	void 이미_가려진_표시는_그대로_두고_건수에_세지_않아요() {
		SensitiveMasker.Result result = masker.mask("[이름]이 [전화번호]로 연락했어");

		assertThat(result.text()).isEqualTo("[이름]이 [전화번호]로 연락했어");
		assertThat(result.count()).isZero();
	}

	@Test
	void 가린_건수를_세요() {
		SensitiveMasker.Result result = masker.mask("010-1234-5678 이랑 a@b.com 그리고 02-123-4567");

		assertThat(result.text()).isEqualTo("[전화번호] 이랑 [이메일] 그리고 [전화번호]");
		assertThat(result.count()).isEqualTo(3);
	}

	@Test
	void 가릴_게_없으면_그대로예요() {
		SensitiveMasker.Result result = masker.mask("오늘 진짜 피곤했어 ㅋㅋ 일찍 잘래");

		assertThat(result.text()).isEqualTo("오늘 진짜 피곤했어 ㅋㅋ 일찍 잘래");
		assertThat(result.count()).isZero();
	}
}
