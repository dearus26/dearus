package com.dearus.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 API(2026.11.25). 앱은 부르지 않고 관리자 페이지(/admin)만 불러요. */
@RestController
@RequestMapping("/admin/api")
public class AdminController {

	public record LoginRequest(@NotBlank(message = "꼭 필요해요") String password) {
	}

	public record ReviewRequest(@NotNull(message = "꼭 필요해요") Boolean reviewed) {
	}

	private final AdminAuthService auth;
	private final AdminReportService reports;

	public AdminController(AdminAuthService auth, AdminReportService reports) {
		this.auth = auth;
		this.reports = reports;
	}

	/** POST /admin/api/login — 관리자 비밀번호로 관리자 토큰을 받아요. */
	@PostMapping("/login")
	public AdminTokens.IssuedToken login(@Valid @RequestBody LoginRequest request) {
		return auth.login(request.password());
	}

	/** GET /admin/api/reports — 답변 신고 목록 (status: OPEN · REVIEWED · ALL, 한 번에 50개) */
	@GetMapping("/reports")
	public AdminReportService.Page reports(@RequestParam(defaultValue = "OPEN") AdminReportService.Status status,
		@RequestParam(defaultValue = "0") int offset) {
		return reports.list(status, offset);
	}

	/** POST /admin/api/reports/{reportId}/review — 확인함으로 표시하거나 되돌려요. */
	@PostMapping("/reports/{reportId}/review")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void review(@PathVariable UUID reportId, @Valid @RequestBody ReviewRequest request) {
		reports.review(reportId, request.reviewed());
	}
}
