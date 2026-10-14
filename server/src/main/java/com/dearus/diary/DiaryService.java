package com.dearus.diary;

import com.dearus.auth.UserRepository;
import com.dearus.common.ApiException;
import com.dearus.common.Kst;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiaryService {

	/** 한 번에 볼 수 있는 기간(일). 홈 캘린더는 한 달치만 받아요. */
	static final int MAX_RANGE_DAYS = 93;

	private final DiaryRepository diaries;
	private final UserRepository users;

	public DiaryService(DiaryRepository diaries, UserRepository users) {
		this.diaries = diaries;
		this.users = users;
	}

	@Transactional(readOnly = true)
	public DiaryListResponse list(UUID userId, LocalDate from, LocalDate to) {
		if (from.isAfter(to)) {
			throw ApiException.validation("from", "시작 날짜가 끝 날짜보다 늦을 수 없어요");
		}
		if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
			throw ApiException.validation("to", "한 번에 " + MAX_RANGE_DAYS + "일까지만 볼 수 있어요");
		}
		return new DiaryListResponse(diaries.findByUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(userId, from, to)
			.stream().map(DiaryResponse::from).toList());
	}

	@Transactional
	public DiaryResponse create(UUID userId, DiaryCreateRequest request) {
		if (request.date().isAfter(Kst.today())) {
			throw ApiException.validation("date", "미래 날짜의 일기는 쓸 수 없어요");
		}
		// 탈퇴한 계정의 토큰이면 저장 대신 다시 로그인하게 해요
		if (!users.existsById(userId)) {
			throw ApiException.unauthorized();
		}
		diaries.findByUserIdAndDiaryDate(userId, request.date()).ifPresent(existing -> {
			throw new ApiException(HttpStatus.CONFLICT, "DIARY_ALREADY_EXISTS", "이 날짜에 쓴 일기가 이미 있어요.",
				Map.of("diaryId", existing.getId()));
		});

		Diary diary = Diary.create(userId, request.date(), request.content(), emotionsOrEmpty(request.emotions()));
		return DiaryResponse.from(diaries.saveAndFlush(diary));
	}

	@Transactional
	public DiaryResponse update(UUID userId, UUID diaryId, DiaryUpdateRequest request) {
		Diary diary = diaries.findByIdAndUserId(diaryId, userId).orElseThrow(ApiException::notFound);
		diary.update(request.content(), emotionsOrEmpty(request.emotions()));
		return DiaryResponse.from(diaries.saveAndFlush(diary));
	}

	@Transactional
	public void delete(UUID userId, UUID diaryId) {
		Diary diary = diaries.findByIdAndUserId(diaryId, userId).orElseThrow(ApiException::notFound);
		diaries.delete(diary);
	}

	private static Set<Emotion> emotionsOrEmpty(Set<Emotion> emotions) {
		return emotions == null ? Set.of() : emotions;
	}
}
