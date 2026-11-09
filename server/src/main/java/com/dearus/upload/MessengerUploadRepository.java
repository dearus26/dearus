package com.dearus.upload;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessengerUploadRepository extends JpaRepository<MessengerUpload, UUID> {

	Optional<MessengerUpload> findByIdAndUserId(UUID id, UUID userId);

	List<MessengerUpload> findByUserIdOrderByUploadedAtDesc(UUID userId);

	/** since 이후에 받은 업로드 중 아직 요약 중인 것이 있는지 (같은 사람이 AI 요청을 겹쳐 보내지 못하게 해요) */
	boolean existsByUserIdAndStatusAndUploadedAtAfter(UUID userId, MessengerStatus status, OffsetDateTime since);

	/** 요약이 끝날 때 내 말 원문(암호화한 값)을 넣어요. 엔티티에 매핑하지 않은 컬럼이라 따로 써요. */
	@Modifying(flushAutomatically = true)
	@Query(value = "update messenger_uploads set original_enc = :originalEnc where id = :id", nativeQuery = true)
	int storeOriginal(@Param("id") UUID id, @Param("originalEnc") byte[] originalEnc);

	/** 업로드 하나의 내 말 원문(암호화한 값)이에요. 아직 없으면(요약 전, V7 전 업로드) null이에요. 시점 분석이 읽어요. */
	@Query(value = "select original_enc from messenger_uploads where id = :id", nativeQuery = true)
	byte[] findOriginal(@Param("id") UUID id);

	/**
	 * 동의 철회 · 탈퇴 때 쓰는 한 번에 지우기예요. 요약은 DB의 ON DELETE CASCADE로 함께 지워져요.
	 * 같은 트랜잭션에서 먼저 바꾼 값(예: users의 철회 시각)이 이 쿼리 앞에서 저장되도록 flushAutomatically를 켜요.
	 * (clearAutomatically를 켜면 아직 저장 안 된 변경이 버려져서 철회가 사라져요)
	 */
	@Modifying(flushAutomatically = true)
	@Query("delete from MessengerUpload u where u.userId = :userId")
	int deleteAllByUserId(@Param("userId") UUID userId);
}
