package com.dearus.chat;

import com.dearus.auth.CurrentUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UsageController {

	private final ChatUsageService usage;

	public UsageController(ChatUsageService usage) {
		this.usage = usage;
	}

	/** GET /me/usage/today — 오늘 대화 시간 (화면 상단 "오늘 대화 N분 · 하루 30분 정도를 권장해요") */
	@GetMapping("/me/usage/today")
	public UsageResponse today(@AuthenticationPrincipal Jwt jwt) {
		return usage.today(CurrentUser.id(jwt));
	}
}
