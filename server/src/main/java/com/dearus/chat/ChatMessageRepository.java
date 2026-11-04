package com.dearus.chat;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

	/** seq가 afterSeq보다 큰 메시지를 seq 오름차순으로 pageable의 크기만큼 줘요. */
	List<ChatMessage> findBySessionIdAndSeqGreaterThanOrderBySeqAsc(UUID sessionId, int afterSeq, Pageable pageable);

	int countBySessionId(UUID sessionId);

	/** 가장 최근 메시지부터 pageable의 크기만큼이에요(대화 이력을 만들 때 뒤집어서 써요). */
	List<ChatMessage> findBySessionIdOrderBySeqDesc(UUID sessionId, Pageable pageable);

	/** 세션의 모든 메시지를 seq 오름차순으로 줘요(끝내기에서 대화 시간과 내 말 개수를 셀 때 써요). */
	List<ChatMessage> findBySessionIdOrderBySeqAsc(UUID sessionId);

	/**
	 * 한 사람의 [from, to) 사이 대화 시간(초)이에요. 같은 세션에서 바로 앞 메시지와의 간격을 더하되 한 간격은 capSeconds까지만 인정해요
	 * (ChatDuration과 같은 규칙). 간격은 뒤쪽 메시지가 속한 날에 넣어요.
	 */
	@Query(value = """
		select cast(coalesce(sum(least(greatest(extract(epoch from (t.created_at - t.prev_at)), 0), :capSeconds)), 0) as double precision)
		from (
			select m.created_at as created_at,
				lag(m.created_at) over (partition by m.session_id order by m.seq) as prev_at
			from chat_messages m
			join chat_sessions s on s.id = m.session_id
			where s.user_id = :userId
		) t
		where t.prev_at is not null and t.created_at >= :from and t.created_at < :to
		""", nativeQuery = true)
	Double sumGapSeconds(@Param("userId") UUID userId, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to,
		@Param("capSeconds") int capSeconds);

	/** 세션의 첫 메시지(seq 1, 페르소나의 첫 인사)예요. */
	Optional<ChatMessage> findFirstBySessionIdOrderBySeqAsc(UUID sessionId);

	/** 세션마다 마지막 메시지 하나씩이에요(목록의 미리보기와 마지막 시각). 메시지가 없는 세션은 빠져요. */
	@Query("select m from ChatMessage m where m.id in "
		+ "(select max(x.id) from ChatMessage x where x.sessionId in :sessionIds group by x.sessionId)")
	List<ChatMessage> findLastMessages(@Param("sessionIds") Collection<UUID> sessionIds);
}
