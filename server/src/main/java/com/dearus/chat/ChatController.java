package com.dearus.chat;

import com.dearus.auth.CurrentUser;
import com.dearus.common.ApiException;
import com.dearus.common.ErrorResponse;
import com.dearus.common.ai.AiException;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class ChatController {

	private static final Logger log = LoggerFactory.getLogger(ChatController.class);
	/** SSE 연결을 열어 두는 최대 시간. 지나도 답변은 끝까지 만들어 저장해요. */
	private static final long SSE_TIMEOUT_MILLIS = 120_000;

	private final ChatService chatService;
	private final ChatReplyService replyService;
	private final Executor streamExecutor;

	public ChatController(ChatService chatService, ChatReplyService replyService,
		@Qualifier("chatStreamExecutor") Executor streamExecutor) {
		this.chatService = chatService;
		this.replyService = replyService;
		this.streamExecutor = streamExecutor;
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

	/**
	 * POST /chats/{chatId}/messages — 말 걸기. 기본은 SSE(token · safety · done · error)로 답변을 조금씩 받고,
	 * Accept가 application/json뿐이면 다 만든 뒤 한 번에 받아요. 검증 오류는 스트림을 열기 전에 일반 HTTP 오류로 나가요.
	 */
	@PostMapping("/chats/{chatId}/messages")
	public Object send(
		@AuthenticationPrincipal Jwt jwt,
		@PathVariable UUID chatId,
		@Valid @RequestBody SendMessageRequest request,
		@RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
		ChatReplyService.Prepared prepared = replyService.prepare(CurrentUser.id(jwt), chatId, request.content());
		if (wantsJson(accept)) {
			try {
				return replyService.run(prepared, ChatReplyService.Sink.NONE);
			} catch (AiException e) {
				throw ChatReplyService.toApiException(e);
			}
		}
		SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
		try {
			streamExecutor.execute(() -> streamTo(emitter, prepared));
		} catch (RejectedExecutionException e) {
			replyService.release(prepared);
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SERVER_BUSY", "지금 대화가 많아요. 잠시 뒤에 다시 보내 주세요.");
		}
		return emitter;
	}

	/** Accept가 application/json뿐일 때만 JSON이에요(비어 있거나, 모든 형식(별표/별표)을 받거나, text/event-stream이 있으면 SSE). */
	static boolean wantsJson(String accept) {
		return accept != null && accept.contains("application/json") && !accept.contains("text/event-stream") && !accept.contains("*/*");
	}

	private void streamTo(SseEmitter emitter, ChatReplyService.Prepared prepared) {
		SseSink sink = new SseSink(emitter);
		try {
			sink.send("done", replyService.run(prepared, sink));
		} catch (AiException e) {
			ApiException api = ChatReplyService.toApiException(e);
			sink.send("error", new ErrorResponse(api.getCode(), api.getMessage(), null));
		} catch (ApiException e) {
			sink.send("error", new ErrorResponse(e.getCode(), e.getMessage(), e.getDetails()));
		} catch (RuntimeException e) {
			log.error("대화 답변 중 예상하지 못한 오류 sessionId={}", prepared.chatId(), e);
			sink.send("error", new ErrorResponse("INTERNAL_ERROR", "서버에 문제가 생겼어요. 다시 보내 주세요.", null));
		} finally {
			sink.complete();
		}
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
