package com.dearus.diary;

import com.dearus.auth.CurrentUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DiaryController {

	private final DiaryService diaryService;

	public DiaryController(DiaryService diaryService) {
		this.diaryService = diaryService;
	}

	/** GET /diaries?from&to — 기간별 일기 (홈 캘린더) */
	@GetMapping("/diaries")
	public DiaryListResponse list(
		@AuthenticationPrincipal Jwt jwt,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return diaryService.list(CurrentUser.id(jwt), from, to);
	}

	/** POST /diaries — 일기 쓰기 */
	@PostMapping("/diaries")
	public ResponseEntity<DiaryResponse> create(
		@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DiaryCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(diaryService.create(CurrentUser.id(jwt), request));
	}

	/** PUT /diaries/{diaryId} — 일기 고치기 */
	@PutMapping("/diaries/{diaryId}")
	public DiaryResponse update(
		@AuthenticationPrincipal Jwt jwt, @PathVariable UUID diaryId, @Valid @RequestBody DiaryUpdateRequest request) {
		return diaryService.update(CurrentUser.id(jwt), diaryId, request);
	}

	/** DELETE /diaries/{diaryId} — 일기 지우기 */
	@DeleteMapping("/diaries/{diaryId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID diaryId) {
		diaryService.delete(CurrentUser.id(jwt), diaryId);
	}
}
