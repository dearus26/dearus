package com.dearus.couple;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoupleRepository extends JpaRepository<Couple, UUID> {

	/** 기다리는 초대 중에서 코드로 찾아요(기다리는 초대끼리는 코드가 겹치지 않아요, V8의 부분 유니크 인덱스). */
	Optional<Couple> findByInviteCodeAndStatus(String inviteCode, CoupleStatus status);
}
