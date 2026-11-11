package com.dearus.couple;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** couple_members의 복합 키 (couple_id, user_id). JPA의 @IdClass라서 필드 이름이 엔티티와 같아야 해요. */
public class CoupleMemberId implements Serializable {

	private UUID coupleId;
	private UUID userId;

	protected CoupleMemberId() {
	}

	public CoupleMemberId(UUID coupleId, UUID userId) {
		this.coupleId = coupleId;
		this.userId = userId;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof CoupleMemberId id && Objects.equals(coupleId, id.coupleId) && Objects.equals(userId, id.userId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(coupleId, userId);
	}
}
