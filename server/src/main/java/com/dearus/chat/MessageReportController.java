package com.dearus.chat;

import com.dearus.auth.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MessageReportController {

	private final MessageReportService service;

	public MessageReportController(MessageReportService service) {
		this.service = service;
	}

	/** POST /chats/{chatId}/messages/{seq}/report — 부적절한 답변 신고 (204) */
	@PostMapping("/chats/{chatId}/messages/{seq}/report")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void report(
		@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID chatId,
		@PathVariable int seq,
		@Valid @RequestBody MessageReportRequest request) {
		service.report(CurrentUser.id(jwt), chatId, seq, request);
	}
}
