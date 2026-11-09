package com.dearus.upload;

import com.dearus.auth.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MessengerController {

	private final MessengerService messengerService;

	public MessengerController(MessengerService messengerService) {
		this.messengerService = messengerService;
	}

	/** POST /uploads/messenger — txt에서 앱이 추린 내 말 글자(JSON)를 올려요(사진은 415). 202로 받고 GET으로 상태를 확인해요. */
	@PostMapping("/uploads/messenger")
	public ResponseEntity<MessengerUploadResponse> upload(
		@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MessengerUploadRequest request) {
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(messengerService.accept(CurrentUser.id(jwt), request));
	}

	/**
	 * POST /uploads/messenger/images — 카카오톡 스크린샷을 대화 순서대로 올려요(multipart의 source · consent · images).
	 * 202로 받고 GET으로 상태를 확인해요. 값이 없거나 틀린 경우의 응답은 서비스가 정해서 required를 끄고 받아요.
	 */
	@PostMapping(path = "/uploads/messenger/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<MessengerUploadResponse> uploadImages(@AuthenticationPrincipal Jwt jwt,
		@RequestParam(name = "source", required = false) MessengerSource source,
		@RequestParam(name = "consent", required = false) Boolean consent,
		@RequestParam(name = "images", required = false) List<MultipartFile> images) {
		return ResponseEntity.status(HttpStatus.ACCEPTED)
			.body(messengerService.acceptImages(CurrentUser.id(jwt), source, consent, images));
	}

	/** GET /uploads/messenger — 올린 대화 목록(설정의 "내 데이터 보기") */
	@GetMapping("/uploads/messenger")
	public MessengerUploadListResponse list(@AuthenticationPrincipal Jwt jwt) {
		return messengerService.list(CurrentUser.id(jwt));
	}

	/** GET /uploads/messenger/{uploadId} — 분석 상태 · 결과 */
	@GetMapping("/uploads/messenger/{uploadId}")
	public MessengerUploadResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID uploadId) {
		return messengerService.get(CurrentUser.id(jwt), uploadId);
	}

	/** DELETE /uploads/messenger/{uploadId} — 이 업로드의 요약 지우기 */
	@DeleteMapping("/uploads/messenger/{uploadId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID uploadId) {
		messengerService.delete(CurrentUser.id(jwt), uploadId);
	}
}
