package com.dearus.chat;

import com.dearus.auth.UserRepository;
import com.dearus.common.ApiException;
import com.dearus.common.Kst;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** GET /me/usage/today — 오늘 대화 시간. 규칙은 {@link ChatDuration}과 같아요(진행 중인 대화도 지금까지 주고받은 만큼 잡혀요). */
@Service
public class ChatUsageService {

	private final UserRepository users;
	private final ChatMessageRepository messages;
	private final UsageProperties properties;

	public ChatUsageService(UserRepository users, ChatMessageRepository messages, UsageProperties properties) {
		this.users = users;
		this.messages = messages;
		this.properties = properties;
	}

	@Transactional(readOnly = true)
	public UsageResponse today(UUID userId) {
		users.findById(userId).orElseThrow(ApiException::unauthorized); // 탈퇴한 계정의 토큰이 남아 있을 수 있어요
		LocalDate today = Kst.today();
		OffsetDateTime from = today.atStartOfDay(Kst.ZONE).toOffsetDateTime();
		OffsetDateTime to = today.plusDays(1).atStartOfDay(Kst.ZONE).toOffsetDateTime();
		Double seconds = messages.sumGapSeconds(userId, from, to, (int) ChatDuration.MAX_GAP.toSeconds());
		int minutes = ChatDuration.minutes(Duration.ofMillis(Math.round((seconds == null ? 0 : seconds) * 1000)));
		return new UsageResponse(today, minutes, properties.recommendedMinutes());
	}
}
