package com.dearus.couple;

import static com.dearus.diary.Emotion.ANGRY;
import static com.dearus.diary.Emotion.ANXIOUS;
import static com.dearus.diary.Emotion.CALM;
import static com.dearus.diary.Emotion.JOY;
import static com.dearus.diary.Emotion.SAD;
import static com.dearus.diary.Emotion.TIRED;
import static org.assertj.core.api.Assertions.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dearus.auth.User;
import com.dearus.common.Kst;
import com.dearus.diary.Diary;
import com.dearus.diary.DiaryRepository;
import com.dearus.persona.MessengerDays;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 함께 볼 날의 추천 규칙(한쪽이라도 화남 · 슬픔 · 불안 + 감정이 다르거나 둘 다 화남)과,
 * 두 사람 모두 기록(일기 또는 그날 올린 메신저 말)이 있는 날만 돌려주는 규칙(2026.11.28)을 확인해요.
 */
class CoupleDayServiceTest {

	private final CoupleAccess access = mock(CoupleAccess.class);
	private final DiaryRepository diaries = mock(DiaryRepository.class);
	private final MessengerDays messengerDays = mock(MessengerDays.class);
	private final CoupleDayService service = new CoupleDayService(access, diaries, messengerDays);
	private final UUID meId = UUID.randomUUID();
	private final UUID partnerId = UUID.randomUUID();
	private final LocalDate d1 = Kst.today().minusDays(30);
	private final LocalDate d2 = d1.plusDays(1);
	private final LocalDate d3 = d1.plusDays(2);
	private final LocalDate d4 = d1.plusDays(3);

	private User user(UUID id) {
		User user = User.create("dev", "dbtest-" + id, "시험");
		ReflectionTestUtils.setField(user, "id", id);
		return user;
	}

	private Diary diary(UUID owner, LocalDate date, com.dearus.diary.Emotion... emotions) {
		return Diary.create(owner, date, "일기", Set.of(emotions));
	}

	@Test
	void 두_사람_모두_일기나_그날_올린_메신저가_있는_날만_돌려줘요() {
		when(access.requireOpen(meId)).thenReturn(new CoupleAccess.Link(UUID.randomUUID(), user(meId), user(partnerId), true));
		// d1: 둘 다 일기 / d2: 나는 일기, 연인은 메신저만 / d3: 둘 다 메신저만 / d4: 나만 일기
		when(diaries.findByUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(eq(meId), any(), any()))
			.thenReturn(List.of(diary(meId, d1, SAD), diary(meId, d2, ANGRY), diary(meId, d4, JOY)));
		when(diaries.findByUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(eq(partnerId), any(), any()))
			.thenReturn(List.of(diary(partnerId, d1, JOY)));
		when(messengerDays.withLines(eq(meId), any(), any())).thenReturn(Set.of(d3));
		when(messengerDays.withLines(eq(partnerId), any(), any())).thenReturn(Set.of(d2, d3));

		CoupleDayListResponse response = service.days(meId, d1, d4);

		assertThat(response.items()).extracting(CoupleDayResponse::date).containsExactly(d1, d2, d3);
		// 메신저만 있는 쪽은 감정이 비어 있고, 감정으로 고르는 추천은 일기가 있는 쪽 감정으로만 봐요
		assertThat(response.items().get(0).recommended()).isTrue(); // 슬픔 · 기쁨
		assertThat(response.items().get(1).myEmotions()).containsExactly(ANGRY);
		assertThat(response.items().get(1).partnerEmotions()).isEmpty();
		assertThat(response.items().get(2).myEmotions()).isEmpty();
		assertThat(response.items().get(2).partnerEmotions()).isEmpty();
		assertThat(response.items().get(2).recommended()).isFalse();
	}

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
