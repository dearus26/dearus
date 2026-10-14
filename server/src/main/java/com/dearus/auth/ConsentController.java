package com.dearus.auth;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConsentController {

	private final ConsentService consentService;

	public ConsentController(ConsentService consentService) {
		this.consentService = consentService;
	}

	/** PUT /me/consent — 서비스 이용 동의 (로그인 직후 동의 화면의 "동의하고 시작하기") */
	@PutMapping("/me/consent")
	public UserResponse agree(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConsentRequest request) {
		return consentService.agree(CurrentUser.id(jwt), request);
	}

	/** DELETE /me/consent — AI 분석 동의 철회 (설정). 철회한 뒤에도 PUT으로 다시 동의할 수 있어요. */
	@DeleteMapping("/me/consent")
	public UserResponse revokeAiConsent(@AuthenticationPrincipal Jwt jwt) {
		return consentService.revokeAiConsent(CurrentUser.id(jwt));
	}
}
