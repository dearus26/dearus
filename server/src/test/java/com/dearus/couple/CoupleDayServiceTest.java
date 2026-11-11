package com.dearus.couple;

import static com.dearus.diary.Emotion.ANGRY;
import static com.dearus.diary.Emotion.ANXIOUS;
import static com.dearus.diary.Emotion.CALM;
import static com.dearus.diary.Emotion.JOY;
import static com.dearus.diary.Emotion.SAD;
import static com.dearus.diary.Emotion.TIRED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 함께 볼 날의 추천 규칙(한쪽이라도 화남 · 슬픔 · 불안 + 감정이 다르거나 둘 다 화남)을 확인해요. */
class CoupleDayServiceTest {

	@Test
	void 한쪽이_힘들고_감정이_다르면_추천해요() {
		assertThat(CoupleDayService.recommended(List.of(SAD), List.of(JOY))).isTrue();
		assertThat(CoupleDayService.recommended(List.of(ANXIOUS, TIRED), List.of(TIRED))).isTrue();
		assertThat(CoupleDayService.recommended(List.of(CALM), List.of(ANGRY))).isTrue();
	}

	@Test
	void 둘_다_화났으면_감정이_같아도_추천해요() {
		assertThat(CoupleDayService.recommended(List.of(ANGRY), List.of(ANGRY))).isTrue();
	}

	@Test
	void 힘든_감정이_없거나_같은_감정이면_추천하지_않아요() {
		assertThat(CoupleDayService.recommended(List.of(JOY), List.of(CALM))).isFalse();
		assertThat(CoupleDayService.recommended(List.of(SAD), List.of(SAD))).isFalse();
		assertThat(CoupleDayService.recommended(List.of(), List.of())).isFalse();
	}
}
