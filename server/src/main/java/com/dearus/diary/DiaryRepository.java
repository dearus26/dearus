package com.dearus.diary;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryRepository extends JpaRepository<Diary, UUID> {

	/** 감정 태그를 한 번에 같이 읽어요 (일기마다 따로 조회하지 않게). */
	@EntityGraph(attributePaths = "emotions")
	List<Diary> findByUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(UUID userId, LocalDate from, LocalDate to);

	/** 여러 날짜의 일기를 한 번에 읽어요(대화 목록의 그날 감정). 감정 태그도 같이 읽어요. */
	@EntityGraph(attributePaths = "emotions")
	List<Diary> findByUserIdAndDiaryDateIn(UUID userId, Collection<LocalDate> diaryDates);

	Optional<Diary> findByUserIdAndDiaryDate(UUID userId, LocalDate diaryDate);

	@EntityGraph(attributePaths = "emotions")
	Optional<Diary> findByIdAndUserId(UUID id, UUID userId);
}
