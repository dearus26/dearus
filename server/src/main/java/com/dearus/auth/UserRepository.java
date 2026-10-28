package com.dearus.auth;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByProviderAndProviderUserId(String provider, String providerUserId);

	/** cutoff보다 먼저 AI 분석 동의를 철회했고 아직 다시 동의하지 않은 사용자예요(보관 기간이 끝난 사람). */
	@Query("select u.id from User u where u.aiRevokedAt is not null and u.aiRevokedAt < :cutoff")
	List<UUID> findIdsRevokedBefore(@Param("cutoff") OffsetDateTime cutoff);
}
