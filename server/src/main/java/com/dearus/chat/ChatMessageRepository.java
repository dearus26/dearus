package com.dearus.chat;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	/** seq가 afterSeq보다 큰 메시지를 seq 오름차순으로 pageable의 크기만큼 줘요. */
	List<ChatMessage> findBySessionIdAndSeqGreaterThanOrderBySeqAsc(UUID sessionId, int afterSeq, Pageable pageable);

	int countBySessionId(UUID sessionId);

	/** 세션마다 마지막 메시지 하나씩이에요(목록의 미리보기와 마지막 시각). 메시지가 없는 세션은 빠져요. */
	@Query("select m from ChatMessage m where m.id in "
		+ "(select max(x.id) from ChatMessage x where x.sessionId in :sessionIds group by x.sessionId)")
	List<ChatMessage> findLastMessages(@Param("sessionIds") Collection<UUID> sessionIds);
}
