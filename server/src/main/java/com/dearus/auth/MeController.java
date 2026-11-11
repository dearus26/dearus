package com.dearus.auth;

import com.dearus.common.ApiException;
import com.dearus.couple.CoupleService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

	private final UserRepository users;
	private final CoupleService couples;

	public MeController(UserRepository users, CoupleService couples) {
		this.users = users;
		this.couples = couples;
	}

	/** GET /me — 내 정보 */
	@GetMapping("/me")
	@Transactional(readOnly = true)
	public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
		UUID userId = CurrentUser.id(jwt);
		// 탈퇴한 계정의 토큰이 남아 있을 수 있어서, 사용자가 없으면 로그인이 필요한 것으로 봐요
		User user = users.findById(userId).orElseThrow(ApiException::unauthorized);
		return UserResponse.from(user);
	}

	/**
	 * DELETE /me — 회원 탈퇴. 일기 등 모든 데이터는 DB의 ON DELETE CASCADE로 함께 지워져요.
	 * 커플이 있으면 먼저 연결을 해제해요(이 커플의 대화를 지우고 연인 쪽 상태도 정리돼요).
	 */
	@DeleteMapping("/me")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	public void deleteMe(@AuthenticationPrincipal Jwt jwt) {
		UUID userId = CurrentUser.id(jwt);
		User user = users.findById(userId).orElseThrow(ApiException::unauthorized);
		couples.revoke(userId);
		users.delete(user);
	}
}
