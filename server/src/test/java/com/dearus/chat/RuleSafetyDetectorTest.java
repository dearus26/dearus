package com.dearus.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 규칙 기반 위기 신호 감지가 잡아야 하는 표현은 잡고, 흔한 말은 잡지 않는지 확인해요. */
class RuleSafetyDetectorTest {

	private final RuleSafetyDetector detector = new RuleSafetyDetector();

	@Test
	void 스스로를_해치려는_표현은_잡아요() {
		for (String text : new String[] {
			"요즘 너무 힘들어서 죽고 싶어",
			"죽고싶다는 생각만 들어",
			"그냥 사라지고 싶어",
			"없어지고 싶다",
			"더는 살기 싫어",
			"살고 싶지 않아",
			"자살하고 싶다는 생각이 들어",
			"자해를 했어",
			"극단적 선택을 할까 봐 무서워",
			"극단적선택이라는 말이 계속 떠올라",
			"스스로 목숨을 끊고 싶어",
			"뛰어내리고 싶어",
			"손목을 긋고 싶었어"}) {
			assertThat(detector.detects(text)).as(text).isTrue();
		}
	}

	@Test
	void 흔한_과장_표현과_일상_말은_잡지_않아요() {
		for (String text : new String[] {
			"배고파 죽겠다 ㅋㅋ",
			"시험 망해서 죽을 것 같아",
			"더워 죽겠음",
			"축구에서 자살골 넣었어",
			"번지 점프는 무서워서 뛰어내리는 건 못 해",
			"오늘 진짜 피곤했어",
			"기분 안 좋아서 일찍 잘래",
			"사라진 지갑을 찾았어",
			""}) {
			assertThat(detector.detects(text)).as(text).isFalse();
		}
	}

	@Test
	void null은_잡지_않아요() {
		assertThat(detector.detects(null)).isFalse();
	}

	@Test
	void 알려진_한계_농담도_걸려요() {
		// 놓치는 것보다 걸리는 쪽이 낫다고 보고 두었어요. 정교화는 나중에 해요(DearUs_나중에처리할사항.md)
		assertThat(detector.detects("죽고 싶을 만큼 웃겨")).isTrue();
	}
}
