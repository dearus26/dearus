package com.dearus.chat;

import com.dearus.auth.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

	private final ChatService chatService;

	public ChatController(ChatService chatService) {
		this.chatService = chatService;
	}

	/** POST /chats — 과거의 나와 대화 시작 (201). 분석이 필요하면 PREPARING이고 GET으로 상태를 확인해요. */
	@PostMapping("/chats")
	public ResponseEntity<ChatSessionResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChatCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(chatService.create(CurrentUser.id(jwt), request));
	}

	/** GET /chats — "과거의 나" 목록 */
	@GetMapping("/chats")
	public ChatListResponse list(@AuthenticationPrincipal Jwt jwt) {
		return chatService.list(CurrentUser.id(jwt));
	}

	/** GET /chats/{chatId} — 세션 상태 */
	@GetMapping("/chats/{chatId}")
	public ChatSessionResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID chatId) {
		return chatService.get(CurrentUser.id(jwt), chatId);
	}

	/** GET /chats/{chatId}/messages — 대화 내용 */
	@GetMapping("/chats/{chatId}/messages")
	public MessageListResponse messages(
		@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID chatId,
		@RequestParam(defaultValue = "0") int afterSeq,
		@RequestParam(defaultValue = "50") int limit) {
		return chatService.messages(CurrentUser.id(jwt), chatId, afterSeq, limit);
	}
}
