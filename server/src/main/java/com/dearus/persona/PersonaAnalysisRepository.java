package com.dearus.persona;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonaAnalysisRepository extends JpaRepository<PersonaAnalysis, UUID> {

	Optional<PersonaAnalysis> findByUserIdAndAnchorDateAndWindowDays(UUID userId, LocalDate anchorDate, short windowDays);

	/**
	 * 동의 철회 때 저장된 분석을 한 번에 지워요. 같은 트랜잭션에서 먼저 바꾼 값(users의 철회 시각)이 이 쿼리 앞에서
	 * 저장되도록 flushAutomatically를 켜요(clearAutomatically는 쓰지 않아요 — 저장 안 된 변경이 버려져요).
	 */
	@Modifying(flushAutomatically = true)
	@Query("delete from PersonaAnalysis p where p.userId = :userId")
	int deleteAllByUserId(@Param("userId") UUID userId);
}
