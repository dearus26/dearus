package com.dearus.persona;

import com.dearus.auth.CurrentUser;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PersonaController {

	private final PersonaWindowService windows;

	public PersonaController(PersonaWindowService windows) {
		this.windows = windows;
	}

	/** GET /personas/shortcuts — 시점 고르기의 바로가기 3개 */
	@GetMapping("/personas/shortcuts")
	public ShortcutListResponse shortcuts(@AuthenticationPrincipal Jwt jwt) {
		return windows.shortcuts(CurrentUser.id(jwt));
	}

	/** GET /personas/preview?anchor&scope — 고른 날짜의 기록량 (scope 기본값은 DAY) */
	@GetMapping("/personas/preview")
	public PersonaWindowResponse preview(
		@AuthenticationPrincipal Jwt jwt,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate anchor,
		@RequestParam(defaultValue = "DAY") PersonaScope scope) {
		return windows.window(CurrentUser.id(jwt), scope, anchor);
	}
}
