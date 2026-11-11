package com.dearus.couple;

import com.dearus.auth.CurrentUser;
import com.dearus.common.ApiException;
import com.dearus.persona.PersonaScope;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 커플 모드 API 7개(API 명세 couples 태그). 상대 페르소나와 대화하는 세션은 POST /chats(forCouple: true)로 만들어요. */
@RestController
public class CoupleController {

	private final CoupleService couples;
	private final CoupleDayService days;
	private final CoupleComparisonService comparisons;

	public CoupleController(CoupleService couples, CoupleDayService days, CoupleComparisonService comparisons) {
		this.couples = couples;
		this.days = days;
		this.comparisons = comparisons;
	}

	/** GET /couples/me — 내 커플 상태(연결이 없으면 NONE) */
	@GetMapping("/couples/me")
	public CoupleStateResponse me(@AuthenticationPrincipal Jwt jwt) {
		return couples.state(CurrentUser.id(jwt));
	}

	/** POST /couples/invite — 초대 코드 만들기(24시간) */
	@PostMapping("/couples/invite")
	public CoupleStateResponse invite(@AuthenticationPrincipal Jwt jwt) {
		return couples.invite(CurrentUser.id(jwt));
	}

	/** POST /couples/invite/accept — 받은 코드 입력하기 */
	@PostMapping("/couples/invite/accept")
	public CoupleStateResponse accept(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AcceptInviteRequest request) {
		return couples.accept(CurrentUser.id(jwt), request.inviteCode());
	}

	/** POST /couples/consent — 커플 모드 동의 */
	@PostMapping("/couples/consent")
	public CoupleStateResponse consent(@AuthenticationPrincipal Jwt jwt, @RequestBody CoupleConsentRequest request) {
		if (!Boolean.TRUE.equals(request.agreed())) {
			throw ApiException.validation("agreed", "동의해야 계속할 수 있어요");
		}
		return couples.consent(CurrentUser.id(jwt));
	}

	/** GET /couples/days?from&to — 함께 볼 수 있는 날(두 사람의 일기가 모두 있는 날) */
	@GetMapping("/couples/days")
	public CoupleDayListResponse days(@AuthenticationPrincipal Jwt jwt,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return days.days(CurrentUser.id(jwt), from, to);
	}

	/** GET /couples/comparisons?anchorDate&scope — 같은 날 두 사람의 기억 비교 */
	@GetMapping("/couples/comparisons")
	public CoupleComparisonResponse comparison(@AuthenticationPrincipal Jwt jwt,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate anchorDate,
		@RequestParam(defaultValue = "DAY") PersonaScope scope) {
		return comparisons.compare(CurrentUser.id(jwt), anchorDate, scope);
	}

	/** POST /couples/revoke — 동의 철회 · 연결 해제(이 커플의 대화와 리포트를 지워요) */
	@PostMapping("/couples/revoke")
	public CoupleStateResponse revoke(@AuthenticationPrincipal Jwt jwt) {
		return couples.revoke(CurrentUser.id(jwt));
	}
}
