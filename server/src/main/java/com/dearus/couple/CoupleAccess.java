package com.dearus.couple;

import com.dearus.auth.User;
import com.dearus.auth.UserRepository;
import com.dearus.chat.ChatSession;
import com.dearus.common.ApiException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 커플 접근 규칙을 한 곳에서 확인해요(기획서 6.3, ERD "커플 접근 규칙").
 * - 열림(open): 연결됨(CONNECTED) + 두 사람 모두 동의 + 철회 없음. 함께 볼 날 · 두 시점 비교를 볼 수 있어요.
 * - 연인 페르소나를 쓸 수 있음: 열림 + 연인의 AI 분석 동의가 유효함. 연인의 기록 · 분석이 AI로 가는 일(대화 만들기 · 보내기 · 끝내기)에 필요해요.
 * 아니면 403 COUPLE_ACCESS_DENIED예요. DB만 읽어서 빨라요.
 */
@Component
public class CoupleAccess {

	/** 지금 연결된 커플과 두 사람이에요. */
	public record Link(UUID coupleId, User me, User partner, boolean open) {
	}

	private final CoupleMemberRepository members;
	private final CoupleRepository couples;
	private final UserRepository users;

	public CoupleAccess(CoupleMemberRepository members, CoupleRepository couples, UserRepository users) {
		this.members = members;
		this.couples = couples;
		this.users = users;
	}

	/** 연결된 커플이 있으면 그 연결이에요(동의 여부와 상관없이). 기다리는 초대뿐이거나 연결이 없으면 빈 값이에요. */
	@Transactional(readOnly = true)
	public Optional<Link> activeLink(UUID userId) {
		CoupleMember me = members.findByUserIdAndRevokedAtIsNull(userId).orElse(null);
		if (me == null) {
			return Optional.empty();
		}
		Couple couple = couples.findById(me.getCoupleId()).orElse(null);
		if (couple == null || !couple.isConnected()) {
			return Optional.empty();
		}
		List<CoupleMember> all = members.findByCoupleId(couple.getId());
		CoupleMember partner = all.stream().filter(m -> !m.getUserId().equals(userId) && m.getRevokedAt() == null).findFirst().orElse(null);
		if (partner == null) {
			return Optional.empty();
		}
		User meUser = users.findById(userId).orElse(null);
		User partnerUser = users.findById(partner.getUserId()).orElse(null);
		if (meUser == null || partnerUser == null) {
			return Optional.empty();
		}
		return Optional.of(new Link(couple.getId(), meUser, partnerUser, me.isConsented() && partner.isConsented()));
	}

	/** 열려 있어야 해요(연결됨 + 양측 동의 + 철회 없음). */
	@Transactional(readOnly = true)
	public Link requireOpen(UUID userId) {
		Link link = activeLink(userId).orElseThrow(CoupleAccess::denied);
		if (!link.open()) {
			throw denied();
		}
		return link;
	}

	/** 열려 있고 연인의 AI 분석 동의도 유효해야 해요(연인의 기록 · 분석이 AI로 가요). */
	@Transactional(readOnly = true)
	public Link requirePartnerPersona(UUID userId) {
		Link link = requireOpen(userId);
		if (!link.partner().aiConsentActive()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "COUPLE_ACCESS_DENIED",
				"연인이 AI 분석 동의를 철회해서 지금은 그날의 연인을 만날 수 없어요.");
		}
		return link;
	}

	/**
	 * 커플 세션에 말을 걸거나 끝낼 때 다시 확인해요. 세션의 커플이 지금 연결된 커플이고, 세션의 페르소나 주인이 지금의 연인이어야 해요.
	 * 개인 세션이면 아무것도 하지 않고 빈 값이에요.
	 */
	@Transactional(readOnly = true)
	public Optional<Link> requireForSession(ChatSession session) {
		if (session.getCoupleId() == null) {
			return Optional.empty();
		}
		Link link = requirePartnerPersona(session.getUserId());
		if (!link.coupleId().equals(session.getCoupleId()) || !link.partner().getId().equals(session.getPersonaOwnerId())) {
			throw denied();
		}
		return Optional.of(link);
	}

	static ApiException denied() {
		return new ApiException(HttpStatus.FORBIDDEN, "COUPLE_ACCESS_DENIED", "두 사람이 모두 동의해야 만날 수 있어요.");
	}
}
