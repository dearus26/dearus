package com.dearus.diary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dearus.common.ApiException;
import com.dearus.common.CryptoProperties;
import com.dearus.common.CryptoService;
import com.dearus.common.Kst;
import com.dearus.persona.MessengerDays;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 홈 캘린더(GET /calendar, 2026.11.28)가 일기 날과 메신저 날을 함께 돌려주는지 확인해요. */
class CalendarServiceTest {

	private final DiaryRepository diaries = mock(DiaryRepository.class);
	private final MessengerDays messengerDays = mock(MessengerDays.class);
	private final CryptoService crypto = new CryptoService(new CryptoProperties(Base64.getEncoder().encodeToString(new byte[32])));
	private final CalendarService service = new CalendarService(diaries, messengerDays, crypto);
	private final UUID userId = UUID.randomUUID();
	private final LocalDate d1 = Kst.today().minusDays(20);
	private final LocalDate d2 = d1.plusDays(1);
	private final LocalDate d3 = d1.plusDays(2);

	@Test
	void 일기_날과_메신저_날을_날짜_순서로_합쳐요() {
		// d1: 일기만 / d2: 일기 + 메신저 / d3: 메신저만(추천 감정 · 한 줄 요약)
		when(diaries.findByUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(eq(userId), any(), any())).thenReturn(List.of(
			Diary.create(userId, d1, "일기", Set.of(Emotion.CALM)), Diary.create(userId, d2, "일기", Set.of(Emotion.SAD, Emotion.JOY))));
		when(messengerDays.days(eq(userId), any(), any())).thenReturn(Map.of(
			d2, new MessengerDays.Day(UUID.randomUUID(), List.of(Emotion.TIRED), null),
			d3, new MessengerDays.Day(UUID.randomUUID(), List.of(Emotion.EXCITED, Emotion.ANXIOUS), crypto.encrypt("MT 짐을 쌌어요."))));

		List<CalendarDayResponse> items = service.days(userId, d1, d3).items();

		assertThat(items).extracting(CalendarDayResponse::date).containsExactly(d1, d2, d3);
		assertThat(items.get(0)).isEqualTo(new CalendarDayResponse(d1, true, List.of(Emotion.CALM), false, List.of(), null));
		// 일기에 직접 고른 감정과 AI 추천 감정을 따로 줘요
		assertThat(items.get(1).emotions()).containsExactly(Emotion.JOY, Emotion.SAD);
		assertThat(items.get(1).suggestedEmotions()).containsExactly(Emotion.TIRED);
		assertThat(items.get(2)).isEqualTo(new CalendarDayResponse(d3, false, List.of(), true,
			List.of(Emotion.EXCITED, Emotion.ANXIOUS), "MT 짐을 쌌어요."));
	}

	@Test
	void 기간이_93일을_넘거나_거꾸로면_400이에요() {
		assertThatThrownBy(() -> service.days(userId, d1, d1.plusDays(93))).isInstanceOf(ApiException.class);
		assertThatThrownBy(() -> service.days(userId, d2, d1)).isInstanceOf(ApiException.class);
	}
}
