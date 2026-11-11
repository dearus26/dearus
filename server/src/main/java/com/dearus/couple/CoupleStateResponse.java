package com.dearus.couple;

import com.dearus.common.Kst;
import java.time.OffsetDateTime;
import java.util.UUID;

/** API 명세의 CoupleState. 초대 코드 화면 · 동의 화면 · 설정이 모두 이 응답으로 그려져요. */
public record CoupleStateResponse(
	String status,
	UUID coupleId,
	CoupleRole myRole,
	Invite invite,
	Person me,
	Person partner,
	OffsetDateTime connectedAt,
	boolean compareOpen) {

	public record Invite(String code, OffsetDateTime expiresAt) {
	}

	public record Person(String nickname, boolean consented) {
	}

	public static CoupleStateResponse none() {
		return new CoupleStateResponse("NONE", null, null, null, null, null, null, false);
	}

	/** 내가 만든 코드를 상대가 입력하기를 기다리는 중이에요. 24시간이 지나면 EXPIRED예요. */
	static CoupleStateResponse pending(Couple couple, CoupleMember me, String myNickname, OffsetDateTime now) {
		return new CoupleStateResponse(
			couple.isCodeExpired(now) ? "EXPIRED" : "PENDING",
			couple.getId(),
			me.getRole(),
			new Invite(couple.getInviteCode(), Kst.toKst(couple.getCodeExpiresAt())),
			new Person(myNickname, me.isConsented()),
			null,
			null,
			false);
	}

	/** 두 계정이 연결됐어요. 두 사람이 모두 동의해야 같은 날 비교가 열려요. */
	static CoupleStateResponse connected(Couple couple, CoupleMember me, String myNickname, CoupleMember partner, String partnerNickname) {
		return new CoupleStateResponse(
			"CONNECTED",
			couple.getId(),
			me.getRole(),
			null,
			new Person(myNickname, me.isConsented()),
			new Person(partnerNickname, partner.isConsented()),
			couple.connectedAtKst(),
			me.isConsented() && partner.isConsented());
	}
}
