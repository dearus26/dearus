package com.dearus.couple;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoupleMemberRepository extends JpaRepository<CoupleMember, CoupleMemberId> {

	/** 그 사람의 활성 커플(기다리는 초대 포함)이에요. 한 사람에 하나뿐이에요(V8의 부분 유니크 인덱스). */
	Optional<CoupleMember> findByUserIdAndRevokedAtIsNull(UUID userId);

	List<CoupleMember> findByCoupleId(UUID coupleId);
}
