package com.dearus.auth;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	/** POST /auth/{provider} — 소셜 로그인 (지금은 provider=dev만 동작해요) */
	@PostMapping("/auth/{provider}")
	public AuthResponse login(@PathVariable String provider, @Valid @RequestBody AuthRequest request) {
		return authService.login(provider, request);
	}
}
