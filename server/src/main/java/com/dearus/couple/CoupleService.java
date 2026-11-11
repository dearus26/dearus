package com.dearus.couple;

import com.dearus.auth.User;
import com.dearus.auth.UserRepository;
import com.dearus.chat.ChatSessionRepository;
import com.dearus.common.ApiException;
import com.dearus.common.Kst;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 커플 연결 · 동의 · 상태 · 철회예요(기획서 6.1).
 * ① 초대 코드 만들기(24시간) → ② 상대가 입력해서 연결 → ③ 각자 동의 → (같은 날 비교 · 상대 페르소나 대화) → 철회하면 끊고 이 커플의 대화를 지워요.
 * 한 사람은 활성 커플(기다리는 초대 포함)을 하나만 가져요. 동시에 눌러도 DB의 부분 유니크 인덱스가 막아요.
 * 로그에는 초대 코드 없이 커플 ID와 사용자 ID만 남겨요.
 */
@Service
public class CoupleService {

	private static final Logger log = LoggerFactory.getLogger(CoupleService.class);

	/** 코드가 다른 기다리는 초대와 겹치면 새 코드로 다시 해요(거의 일어나지 않아요). */
	static final int CODE_ATTEMPTS = 5;

	private final CoupleRepository couples;
	private final CoupleMemberRepository members;
	private final UserRepository users;
	private final ChatSessionRepository chatSessions;
	private final TransactionTemplate tx;

	public CoupleService(CoupleRepository couples, CoupleMemberRepository members, UserRepository users,
		ChatSessionRepository chatSessions, TransactionTemplate tx) {
		this.couples = couples;
		this.members = members;
		this.users = users;
		this.chatSessions = chatSessions;
		this.tx = tx;
	}

	/** GET /couples/me */
	@Transactional(readOnly = true)
	public CoupleStateResponse state(UUID userId) {
		User me = users.findById(userId).orElseThrow(ApiException::unauthorized);
		CoupleMember mine = members.findByUserIdAndRevokedAtIsNull(userId).orElse(null);
		if (mine == null) {
			return CoupleStateResponse.none();
		}
		Couple couple = couples.findById(mine.getCoupleId()).orElse(null);
		if (couple == null) {
			return CoupleStateResponse.none();
		}
		if (couple.isPending()) {
			return CoupleStateResponse.pending(couple, mine, me.getNickname(), Kst.now());
		}
		if (!couple.isConnected()) {
			return CoupleStateResponse.none();
		}
		CoupleMember partner = partnerOf(couple.getId(), userId);
		if (partner == null) {
			return CoupleStateResponse.none();
		}
		String partnerNickname = users.findById(partner.getUserId()).map(User::getNickname).orElse("");
		return CoupleStateResponse.connected(couple, mine, me.getNickname(), partner, partnerNickname);
	}

	/**
	 * POST /couples/invite — 아직 유효한 코드가 있으면 그 코드, 만료됐으면 새 코드로 바꾸고, 없으면 새로 만들어요.
	 * 이미 연결된 커플이 있으면 409 ALREADY_IN_COUPLE이에요.
	 */
	public CoupleStateResponse invite(UUID userId) {
		users.findById(userId).orElseThrow(ApiException::unauthorized);
		for (int attempt = 1; attempt <= CODE_ATTEMPTS; attempt++) {
			try {
				tx.executeWithoutResult(status -> inviteOnce(userId));
				return state(userId);
			} catch (DataIntegrityViolationException e) {
				// 코드가 겹쳤거나, 같은 사람이 동시에 두 번 눌러 활성 커플이 둘이 되려 했어요. 다시 보면 어느 쪽인지 알 수 있어요.
				log.info("초대 코드를 다시 만들어요 userId={} attempt={}", userId, attempt);
			}
		}
		throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SERVER_BUSY", "초대 코드를 만들지 못했어요. 잠시 뒤에 다시 눌러 주세요.");
	}

	private void inviteOnce(UUID userId) {
		OffsetDateTime now = Kst.now();
		CoupleMember mine = members.findByUserIdAndRevokedAtIsNull(userId).orElse(null);
		if (mine != null) {
			Couple couple = couples.findById(mine.getCoupleId()).orElseThrow(ApiException::notFound);
			if (couple.isConnected()) {
				throw alreadyInCouple();
			}
			if (couple.isPending() && couple.isCodeExpired(now)) {
				couple.renewCode(InviteCodes.generate(), now);
				couples.saveAndFlush(couple);
			}
			return; // 아직 유효한 코드는 그대로 돌려줘요
		}
		Couple couple = couples.saveAndFlush(Couple.invite(InviteCodes.generate(), now));
		members.saveAndFlush(CoupleMember.join(couple.getId(), userId, CoupleRole.INVITER, now));
		log.info("초대 코드를 만들었어요 coupleId={} userId={}", couple.getId(), userId);
	}

	/** POST /couples/invite/accept — 받은 코드로 연결해요. 내가 만들어 둔 코드(연결 전)가 있었으면 취소해요. */
	public CoupleStateResponse accept(UUID userId, String rawCode) {
		String code = InviteCodes.normalize(rawCode);
		if (code == null) {
			throw ApiException.validation("inviteCode", "DU-XXXX-XXXX 형식이에요");
		}
		users.findById(userId).orElseThrow(ApiException::unauthorized);
		try {
			tx.executeWithoutResult(status -> acceptOnce(userId, code));
		} catch (DataIntegrityViolationException e) {
			// 같은 코드를 두 사람이 동시에 넣었거나, 내가 동시에 다른 커플에 들어가려 했어요
			throw alreadyInCouple();
		}
		return state(userId);
	}

	private void acceptOnce(UUID userId, String code) {
		OffsetDateTime now = Kst.now();
		Couple couple = couples.findByInviteCodeAndStatus(code, CoupleStatus.PENDING)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COUPLE_CODE_INVALID", "없는 코드예요. 다시 확인해 주세요."));
		List<CoupleMember> inviters = members.findByCoupleId(couple.getId());
		if (inviters.stream().anyMatch(m -> m.getUserId().equals(userId))) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "COUPLE_OWN_CODE", "내가 만든 코드는 입력할 수 없어요. 상대에게 코드를 보내 주세요.");
		}
		if (couple.isCodeExpired(now)) {
			throw new ApiException(HttpStatus.GONE, "COUPLE_CODE_EXPIRED", "만료된 코드예요. 상대에게 새 코드를 받아 주세요.");
		}
		CoupleMember mine = members.findByUserIdAndRevokedAtIsNull(userId).orElse(null);
		if (mine != null) {
			Couple myCouple = couples.findById(mine.getCoupleId()).orElse(null);
			if (myCouple != null && myCouple.isConnected()) {
				throw alreadyInCouple();
			}
			if (myCouple != null) {
				couples.delete(myCouple); // 연결 전이던 내 초대는 취소해요(멤버 행은 ON DELETE CASCADE)
				couples.flush();
			}
		}
		members.saveAndFlush(CoupleMember.join(couple.getId(), userId, CoupleRole.INVITEE, now));
		couple.connect(now);
		couples.saveAndFlush(couple);
		log.info("커플이 연결됐어요 coupleId={} userId={}", couple.getId(), userId);
	}

	/** POST /couples/consent — 내 동의를 기록해요. 연결된 커플이 없으면 409 COUPLE_NOT_CONNECTED예요. */
	@Transactional
	public CoupleStateResponse consent(UUID userId) {
		CoupleMember mine = members.findByUserIdAndRevokedAtIsNull(userId).orElseThrow(CoupleService::notConnected);
		Couple couple = couples.findById(mine.getCoupleId()).orElseThrow(CoupleService::notConnected);
		if (!couple.isConnected()) {
			throw notConnected();
		}
		mine.consent(Kst.now());
		members.save(mine);
		return state(userId);
	}

	/**
	 * POST /couples/revoke — 연결된 커플이면 이 커플의 대화 세션(두 사람 것, 메시지 · 리포트 포함)을 지우고 연결을 끊어요(되돌릴 수 없어요).
	 * 연결 전이면 초대를 취소하고, 연결이 없으면 아무 일도 하지 않아요. 내 일기 · 개인 모드 기록 · 내 분석은 그대로예요.
	 * 회원 탈퇴 전에도 불러요(연인 쪽 상태가 이상하게 남지 않게).
	 */
	@Transactional
	public CoupleStateResponse revoke(UUID userId) {
		CoupleMember mine = members.findByUserIdAndRevokedAtIsNull(userId).orElse(null);
		if (mine == null) {
			return CoupleStateResponse.none();
		}
		Couple couple = couples.findById(mine.getCoupleId()).orElse(null);
		if (couple == null) {
			return CoupleStateResponse.none();
		}
		if (!couple.isConnected()) {
			couples.delete(couple); // 연결 전 초대 취소. 멤버 행은 ON DELETE CASCADE
			log.info("초대를 취소했어요 coupleId={} userId={}", couple.getId(), userId);
			return CoupleStateResponse.none();
		}
		OffsetDateTime now = Kst.now();
		int deleted = chatSessions.deleteAllByCoupleId(couple.getId()); // 메시지 · 리포트는 ON DELETE CASCADE
		couple.disconnect(now);
		couples.save(couple);
		for (CoupleMember member : members.findByCoupleId(couple.getId())) {
			member.revoke(now);
			members.save(member);
		}
		log.info("커플 연결을 해제했어요 coupleId={} userId={} deletedSessions={}", couple.getId(), userId, deleted);
		return CoupleStateResponse.none();
	}

	private CoupleMember partnerOf(UUID coupleId, UUID userId) {
		return members.findByCoupleId(coupleId).stream()
			.filter(m -> !m.getUserId().equals(userId) && m.getRevokedAt() == null)
			.findFirst().orElse(null);
	}

	private static ApiException alreadyInCouple() {
		return new ApiException(HttpStatus.CONFLICT, "ALREADY_IN_COUPLE", "이미 연결된 커플이 있어요.");
	}

	private static ApiException notConnected() {
		return new ApiException(HttpStatus.CONFLICT, "COUPLE_NOT_CONNECTED", "연결된 커플이 없어요. 먼저 초대 코드로 연결해 주세요.");
	}
}
