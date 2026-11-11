package com.dearus.chat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

	Optional<ChatSession> findByIdAndUserId(UUID id, UUID userId);

	/** "과거의 나" 목록용이에요. 커플 모드의 같은 날 비교 세션은 목록에 넣지 않아요. */
	List<ChatSession> findByUserIdAndCoupleIdIsNull(UUID userId);

	/** 동의 철회 뒤 보관 기간이 끝났을 때 쓰는 한 번에 지우기예요. 메시지는 DB의 ON DELETE CASCADE로 함께 지워져요. */
	@Modifying(flushAutomatically = true)
	@Query("delete from ChatSession s where s.userId = :userId")
	int deleteAllByUserId(@Param("userId") UUID userId);

	/** 한 사람의 같은 날 커플 세션이에요(두 시점 비교 · 이어서 대화하기). 최근에 시작한 것부터예요. */
	List<ChatSession> findByUserIdAndCoupleIdAndAnchorDateAndWindowDaysOrderByStartedAtDesc(UUID userId, UUID coupleId,
		LocalDate anchorDate, short windowDays);

	/** 커플 철회 · 해제 때 이 커플의 대화를 두 사람 것 모두 지워요. 메시지 · 리포트는 ON DELETE CASCADE로 함께 지워져요. */
	@Modifying(flushAutomatically = true)
	@Query("delete from ChatSession s where s.coupleId = :coupleId")
	int deleteAllByCoupleId(@Param("coupleId") UUID coupleId);

	/**
	 * 연인이 내 과거 페르소나와 나눈 커플 대화예요(페르소나 주인이 나). AI 분석 동의를 철회한 지 30일이 지나 내 분석 데이터를 지울 때 함께 지워요.
	 * 내가 연인의 페르소나와 나눈 대화는 {@link #deleteAllByUserId}가 지워요.
	 */
	@Modifying(flushAutomatically = true)
	@Query("delete from ChatSession s where s.personaOwnerId = :userId and s.coupleId is not null")
	int deleteCoupleSessionsWithPersonaOf(@Param("userId") UUID userId);
}
