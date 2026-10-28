package com.dearus.chat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

	Optional<ChatSession> findByIdAndUserId(UUID id, UUID userId);

	/** "과거의 나" 목록용이에요. 커플 모드의 같은 날 비교 세션은 목록에 넣지 않아요. */
	List<ChatSession> findByUserIdAndCoupleIdIsNull(UUID userId);
}
