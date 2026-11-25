package com.dearus.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.dearus.auth.JwtService;
import com.dearus.auth.User;
import com.dearus.auth.UserRepository;
import com.dearus.chat.ChatMessage;
import com.dearus.chat.ChatMessageRepository;
import com.dearus.chat.ChatRole;
import com.dearus.chat.ChatSession;
import com.dearus.chat.ChatSessionRepository;
import com.dearus.chat.MessageReportReason;
import com.dearus.chat.MessageReportRequest;
import com.dearus.chat.MessageReportService;
import com.dearus.persona.PersonaScope;
import com.dearus.persona.RecordLevel;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 진짜 서버 · DB로 관리자 페이지 흐름을 확인해요(2026.11.25): 페이지 파일이 열리고, 비밀번호로 로그인하고,
 * 신고 목록에 신고된 답과 바로 앞 말만 오고, 확인함 · 되돌리기가 되고, 앱 토큰 · 관리자 토큰을 서로 못 쓰는지요.
 * DB를 건드려서 DEARUS_DB_TEST=true일 때만 돌고, 만든 사용자는 끝나면 지워요(신고 · 대화도 함께 지워져요).
 * 돌리기: DEARUS_DB_TEST=true ./gradlew test --tests '*AdminDbTest' -i
 */
@EnabledIfEnvironmentVariable(named = "DEARUS_DB_TEST", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
	properties = {"app.ai.provider=fake", "app.admin.password=" + AdminDbTest.PASSWORD})
class AdminDbTest {

	static final String PASSWORD = "admin-db-test-password";

	@LocalServerPort int port;
	@Autowired UserRepository users;
	@Autowired ChatSessionRepository sessions;
	@Autowired ChatMessageRepository messages;
	@Autowired MessageReportService reports;
	@Autowired JwtService jwt;
	@Autowired JdbcTemplate jdbc;

	private final HttpClient http = HttpClient.newHttpClient();
	private final JsonMapper json = JsonMapper.builder().build();

	private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
			.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
			.header("Content-Type", "application/json");
		if (token != null) {
			request.header("Authorization", "Bearer " + token);
		}
		return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	@Test
	void 관리자는_신고된_답과_바로_앞_말을_보고_확인함으로_표시해요() throws Exception {
		User user = User.create("dev", "dbtest-" + UUID.randomUUID(), "시험");
		user.agree(false);
		UUID userId = users.saveAndFlush(user).getId();
		try {
			UUID chatId = sessions.saveAndFlush(
				ChatSession.start(userId, LocalDate.of(2026, 5, 1), PersonaScope.DAY, null, RecordLevel.ENOUGH)).getId();
			messages.saveAndFlush(ChatMessage.create(chatId, 1, ChatRole.PERSONA, "안녕 나야"));
			messages.saveAndFlush(ChatMessage.create(chatId, 2, ChatRole.USER, "첫 번째 내 말"));
			messages.saveAndFlush(ChatMessage.create(chatId, 3, ChatRole.PERSONA, "보통 답"));
			messages.saveAndFlush(ChatMessage.create(chatId, 4, ChatRole.USER, "그때 무슨 일 있었어?"));
			messages.saveAndFlush(ChatMessage.create(chatId, 5, ChatRole.PERSONA, "상처 주는 이상한 답"));
			reports.report(userId, chatId, 5, new MessageReportRequest(MessageReportReason.HURTFUL, "이건 너무해요"));
			UUID reportId = jdbc.queryForObject(
				"select r.id from message_reports r join chat_messages m on m.id = r.message_id where m.session_id = ?", UUID.class, chatId);

			// 페이지 파일은 로그인 없이 열리고, 다른 사이트에 끼워 넣지 못하게 막아요
			HttpResponse<String> page = send("GET", "/admin", null, null);
			assertThat(page.statusCode()).isEqualTo(200);
			assertThat(page.body()).contains("Dear Us 관리자");
			assertThat(page.headers().firstValue("Content-Security-Policy")).hasValueSatisfying(v -> assertThat(v).contains("frame-ancestors 'none'"));
			assertThat(send("GET", "/admin/admin.js", null, null).statusCode()).isEqualTo(200);

			// 토큰 없이 · 앱 토큰으로는 관리자 API를 못 써요
			String userToken = jwt.issue(userId).value();
			assertThat(send("GET", "/admin/api/reports", null, null).statusCode()).isEqualTo(401);
			assertThat(send("GET", "/admin/api/reports", userToken, null).statusCode()).isEqualTo(401);

			assertThat(send("POST", "/admin/api/login", null, "{\"password\":\"wrong-password-wrong\"}").statusCode()).isEqualTo(401);
			HttpResponse<String> login = send("POST", "/admin/api/login", null, "{\"password\":\"" + PASSWORD + "\"}");
			assertThat(login.statusCode()).isEqualTo(200);
			String adminToken = json.readTree(login.body()).get("value").asString();

			// 관리자 토큰으로는 앱 API를 못 써요
			assertThat(send("GET", "/me", adminToken, null).statusCode()).isEqualTo(401);
			assertThat(send("GET", "/me", userToken, null).statusCode()).isEqualTo(200);

			JsonNode item = find(send("GET", "/admin/api/reports?status=ALL", adminToken, null), reportId);
			assertThat(item).isNotNull();
			assertThat(item.get("reason").asString()).isEqualTo("HURTFUL");
			assertThat(item.get("detail").asString()).isEqualTo("이건 너무해요");
			assertThat(item.get("reply").asString()).isEqualTo("상처 주는 이상한 답");
			assertThat(item.get("previousUserMessage").asString()).isEqualTo("그때 무슨 일 있었어?");
			assertThat(item.get("mode").asString()).isEqualTo("PERSONAL");
			assertThat(item.get("scope").asString()).isEqualTo("DAY");
			// 신고한 사람이 누구인지와 다른 말은 오지 않아요
			assertThat(item.toString()).doesNotContain(userId.toString()).doesNotContain("첫 번째 내 말").doesNotContain("시험");

			String review = "/admin/api/reports/" + reportId + "/review";
			assertThat(send("POST", review, adminToken, "{\"reviewed\":true}").statusCode()).isEqualTo(204);
			assertThat(find(send("GET", "/admin/api/reports?status=OPEN", adminToken, null), reportId)).isNull();
			assertThat(find(send("GET", "/admin/api/reports?status=REVIEWED", adminToken, null), reportId)).isNotNull();
			assertThat(send("POST", review, adminToken, "{\"reviewed\":false}").statusCode()).isEqualTo(204);
			assertThat(jdbc.queryForObject("select reviewed_at is null from message_reports where id = ?", Boolean.class, reportId)).isTrue();

			assertThat(send("POST", "/admin/api/reports/" + UUID.randomUUID() + "/review", adminToken, "{\"reviewed\":true}").statusCode())
				.isEqualTo(404);
		} finally {
			users.deleteById(userId);
		}
	}

	/** 첫 쪽(최대 50개)에서 이 신고를 찾아요. 공용 DB라 다른 신고가 많으면 못 찾을 수 있어서 ALL · REVIEWED는 최근 것부터예요. */
	private JsonNode find(HttpResponse<String> response, UUID reportId) throws Exception {
		assertThat(response.statusCode()).isEqualTo(200);
		for (JsonNode item : json.readTree(response.body()).get("items")) {
			if (item.get("id").asString().equals(reportId.toString())) {
				return item;
			}
		}
		return null;
	}
}
