package com.dearus.report;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, UUID> {

	Optional<Report> findBySessionId(UUID sessionId);

	/** 한 사람이 from 이후에 AI로 만들기 시작한 리포트 수예요(하루 리포트 상한). 만들지 않은 리포트(NONE)는 세지 않아요. */
	@Query("select count(r) from Report r where r.createdAt >= :from and r.status <> com.dearus.report.ReportStatus.NONE "
		+ "and r.sessionId in (select s.id from ChatSession s where s.userId = :userId)")
	long countGeneratedSince(@Param("userId") UUID userId, @Param("from") OffsetDateTime from);
}
