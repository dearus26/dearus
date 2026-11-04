package com.dearus.report;

import com.dearus.auth.CurrentUser;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportController {

	private final ReportService reports;

	public ReportController(ReportService reports) {
		this.reports = reports;
	}

	/** POST /chats/{chatId}/end — 대화 끝내기. 리포트 만들기를 시작해요(이미 끝난 대화는 같은 결과, 실패한 리포트는 재시도). */
	@PostMapping("/chats/{chatId}/end")
	public EndChatResult end(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID chatId) {
		return reports.end(CurrentUser.id(jwt), chatId);
	}

	/** GET /chats/{chatId}/report — 회고 리포트. 만드는 중이면 202, 1~2초 뒤에 다시 불러요. */
	@GetMapping("/chats/{chatId}/report")
	public ResponseEntity<Object> report(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID chatId) {
		return reports.get(CurrentUser.id(jwt), chatId)
			.<ResponseEntity<Object>>map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.accepted().body(new ReportPending(ReportStatus.GENERATING)));
	}
}
