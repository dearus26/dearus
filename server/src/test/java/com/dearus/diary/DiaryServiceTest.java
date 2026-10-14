package com.dearus.diary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dearus.auth.UserRepository;
import com.dearus.common.ApiException;
import com.dearus.common.Kst;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/** DB 없이 일기 규칙(날짜 · 중복 · 기간 · 소유자)만 확인해요. */
class DiaryServiceTest {

	private final DiaryRepository diaries = mock(DiaryRepository.class);
	private final UserRepository users = mock(UserRepository.class);
	private final DiaryService service = new DiaryService(diaries, users);
	private final UUID userId = UUID.randomUUID();

	private ApiException thrownBy(Runnable action) {
		return (ApiException) catchThrowable(action);
	}

	private Throwable catchThrowable(Runnable action) {
		try {
			action.run();
		} catch (Throwable t) {
			return t;
		}
		throw new AssertionError("예외가 나야 하는데 나지 않았어요");
	}

	@Test
	void 미래_날짜의_일기는_쓸_수_없어요() {
		var request = new DiaryCreateRequest(Kst.today().plusDays(1), "내일 일기", Set.of());

		ApiException e = thrownBy(() -> service.create(userId, request));

		assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(e.getCode()).isEqualTo("VALIDATION_FAILED");
		verify(diaries, never()).saveAndFlush(any());
	}

	@Test
	void 같은_날짜에_이미_일기가_있으면_409와_기존_ID를_돌려줘요() {
		LocalDate date = Kst.today();
		Diary existing = Diary.create(userId, date, "먼저 쓴 일기", Set.of());
		UUID existingId = UUID.randomUUID();
		ReflectionTestUtils.setField(existing, "id", existingId); // DB에 저장되면 생기는 ID
		when(users.existsById(userId)).thenReturn(true);
		when(diaries.findByUserIdAndDiaryDate(userId, date)).thenReturn(Optional.of(existing));

		ApiException e = thrownBy(() -> service.create(userId, new DiaryCreateRequest(date, "또 쓰기", Set.of())));

		assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(e.getCode()).isEqualTo("DIARY_ALREADY_EXISTS");
		assertThat(e.getDetails()).containsEntry("diaryId", existingId);
		verify(diaries, never()).saveAndFlush(any());
	}

	@Test
	void 탈퇴한_계정의_토큰으로는_일기를_쓸_수_없어요() {
		when(users.existsById(userId)).thenReturn(false);

		ApiException e = thrownBy(() -> service.create(userId, new DiaryCreateRequest(Kst.today(), "유령", Set.of())));

		assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void 감정_태그가_없어도_저장돼요() {
		LocalDate date = Kst.today();
		when(users.existsById(userId)).thenReturn(true);
		when(diaries.findByUserIdAndDiaryDate(userId, date)).thenReturn(Optional.empty());
		when(diaries.saveAndFlush(any(Diary.class))).thenAnswer(inv -> inv.getArgument(0));

		DiaryResponse response = service.create(userId, new DiaryCreateRequest(date, "태그 없음", null));

		assertThat(response.emotions()).isEmpty();
		assertThat(response.content()).isEqualTo("태그 없음");
	}

	@Test
	void 응답의_감정_태그는_선언_순서로_정렬돼요() {
		Diary diary = Diary.create(userId, Kst.today(), "정렬", Set.of(Emotion.TIRED, Emotion.JOY, Emotion.CALM));

		assertThat(DiaryResponse.from(diary).emotions()).containsExactly(Emotion.JOY, Emotion.CALM, Emotion.TIRED);
	}

	@Test
	void 시작_날짜가_끝_날짜보다_늦으면_거절해요() {
		LocalDate to = LocalDate.of(2026, 10, 1);

		ApiException e = thrownBy(() -> service.list(userId, to.plusDays(1), to));

		assertThat(e.getCode()).isEqualTo("VALIDATION_FAILED");
	}

	@Test
	void 한_번에_93일까지만_조회할_수_있어요() {
		LocalDate from = LocalDate.of(2026, 1, 1);

		assertThat(service.list(userId, from, from.plusDays(92)).items()).isEmpty();
		ApiException e = thrownBy(() -> service.list(userId, from, from.plusDays(93)));

		assertThat(e.getCode()).isEqualTo("VALIDATION_FAILED");
	}

	@Test
	void 남의_일기_또는_없는_일기는_404예요() {
		UUID diaryId = UUID.randomUUID();
		when(diaries.findByIdAndUserId(diaryId, userId)).thenReturn(Optional.empty());

		assertThat(thrownBy(() -> service.update(userId, diaryId, new DiaryUpdateRequest("수정", Set.of()))).getStatus())
			.isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(thrownBy(() -> service.delete(userId, diaryId)).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void 일기를_고치면_본문과_감정_태그가_통째로_바뀌고_날짜는_그대로예요() {
		LocalDate date = LocalDate.of(2026, 10, 5);
		Diary diary = Diary.create(userId, date, "처음", Set.of(Emotion.SAD));
		UUID diaryId = UUID.randomUUID();
		when(diaries.findByIdAndUserId(diaryId, userId)).thenReturn(Optional.of(diary));
		when(diaries.saveAndFlush(any(Diary.class))).thenAnswer(inv -> inv.getArgument(0));

		DiaryResponse response = service.update(userId, diaryId, new DiaryUpdateRequest("고침", Set.of(Emotion.JOY)));

		assertThat(response.content()).isEqualTo("고침");
		assertThat(response.emotions()).containsExactly(Emotion.JOY);
		assertThat(response.date()).isEqualTo(date);
	}
}
