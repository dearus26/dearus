package com.dearus.chat;

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
}
