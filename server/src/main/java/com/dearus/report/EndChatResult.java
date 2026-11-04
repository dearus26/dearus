package com.dearus.report;

import com.dearus.common.Kst;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * API 명세의 EndChatResult. reportStatus는 GENERATING(만드는 중, GET report가 202), READY(이미 완성),
 * NONE(내 말이 너무 적어서 만들지 않아요) 중 하나예요. 실패한 리포트는 끝내기를 다시 부르면 GENERATING으로 돌아가요.
 */
public record EndChatResult(UUID chatId, OffsetDateTime endedAt, int durationMinutes, ReportStatus reportStatus) {

	public static EndChatResult of(UUID chatId, OffsetDateTime endedAt, Report report) {
		return new EndChatResult(chatId, Kst.toKst(endedAt), report.getDurationMinutes(), report.getStatus());
	}
}
