package com.dearus.upload;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessengerWeeklySummaryRepository extends JpaRepository<MessengerWeeklySummary, UUID> {

	int countByUploadId(UUID uploadId);

	List<MessengerWeeklySummary> findByUploadIdOrderByWeekStartAsc(UUID uploadId);

	/**
	 * 한 사람의 주간 요약 중 weekStart가 from~to(포함)이고 아직 만료되지 않은 것이에요(시점 분석이 읽어요).
	 * 같은 주를 여러 번 올렸으면 그 주의 행이 여러 개라서, 주 오름차순 · 같은 주 안에서는 최근 순으로 줘요.
	 */
	@Query("select s from MessengerWeeklySummary s where s.uploadId in (select u.id from MessengerUpload u where u.userId = :userId) "
		+ "and s.weekStart between :from and :to and s.expiresAt > :now order by s.weekStart asc, s.createdAt desc")
	List<MessengerWeeklySummary> findUsable(@Param("userId") UUID userId, @Param("from") LocalDate from,
		@Param("to") LocalDate to, @Param("now") OffsetDateTime now);
}
