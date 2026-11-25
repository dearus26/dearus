package com.dearus.admin;

import com.dearus.common.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 관리자 페이지의 답변 신고 확인이에요(2026.11.25). 신고된 AI 답변과 그 답을 끌어낸 사용자의 바로 앞 말 하나만 보여줘요
 * (관리자가 보는 대화를 가장 적게 하기로 했어요). 누가 신고했는지(사용자 ID · 닉네임)는 보여주지 않아요.
 * 대화 · 리포트는 평문이라 DB에서 바로 읽어요.
 */
@Service
public class AdminReportService {

	private static final Logger log = LoggerFactory.getLogger(AdminReportService.class);

	/** 한 번에 보여주는 신고 수예요. */
	static final int PAGE_SIZE = 50;

	public enum Status {
		/** 확인하지 않은 신고, 오래된 것부터 */
		OPEN,
		/** 확인한 신고, 최근 것부터 */
		REVIEWED,
		/** 모두, 최근 것부터 */
		ALL
	}

	public record Item(UUID id, OffsetDateTime reportedAt, String reason, String detail, OffsetDateTime reviewedAt, UUID chatId,
		int seq, String mode, String scope, LocalDate anchorDate, String reply, String previousUserMessage) {
	}

	public record Page(long openCount, int offset, boolean hasMore, List<Item> items) {
	}

	private static final String SELECT = """
		select r.id, r.created_at, r.reason, r.detail, r.reviewed_at, s.id as chat_id, m.seq, s.couple_id, s.window_days,
		       s.anchor_date, m.content,
		       (select u.content from chat_messages u
		         where u.session_id = m.session_id and u.seq < m.seq and u.role = 'USER'
		         order by u.seq desc limit 1) as previous_user_message
		  from message_reports r
		  join chat_messages m on m.id = r.message_id
		  join chat_sessions s on s.id = m.session_id
		""";

	private final JdbcTemplate jdbc;

	public AdminReportService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Page list(Status status, int offset) {
		int from = Math.max(0, offset);
		String where = switch (status) {
			case OPEN -> " where r.reviewed_at is null order by r.created_at asc, r.id";
			case REVIEWED -> " where r.reviewed_at is not null order by r.created_at desc, r.id";
			case ALL -> " order by r.created_at desc, r.id";
		};
		// 하나 더 읽어서 다음 쪽이 있는지 알아요
		List<Item> rows = jdbc.query(SELECT + where + " limit ? offset ?", AdminReportService::item, PAGE_SIZE + 1, from);
		boolean hasMore = rows.size() > PAGE_SIZE;
		Long open = jdbc.queryForObject("select count(*) from message_reports where reviewed_at is null", Long.class);
		return new Page(open == null ? 0 : open, from, hasMore, hasMore ? rows.subList(0, PAGE_SIZE) : rows);
	}

	/** 확인함으로 표시하거나(reviewed=true) 되돌려요. 신고가 없으면(대화와 함께 지워졌으면) 404예요. */
	public void review(UUID reportId, boolean reviewed) {
		int updated = jdbc.update(
			"update message_reports set reviewed_at = case when ? then now() else null end where id = ?", reviewed, reportId);
		if (updated == 0) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "신고를 찾지 못했어요. 대화와 함께 지워졌을 수 있어요.");
		}
		log.info("관리자가 신고를 {} reportId={}", reviewed ? "확인했어요" : "확인 전으로 되돌렸어요", reportId);
	}

	private static Item item(ResultSet rs, int row) throws SQLException {
		return new Item(
			rs.getObject("id", UUID.class),
			rs.getObject("created_at", OffsetDateTime.class),
			rs.getString("reason"),
			rs.getString("detail"),
			rs.getObject("reviewed_at", OffsetDateTime.class),
			rs.getObject("chat_id", UUID.class),
			rs.getInt("seq"),
			rs.getObject("couple_id") == null ? "PERSONAL" : "COUPLE",
			rs.getInt("window_days") == 0 ? "DAY" : "AROUND",
			rs.getObject("anchor_date", LocalDate.class),
			rs.getString("content"),
			rs.getString("previous_user_message"));
	}
}
