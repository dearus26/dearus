package com.dearus.diary;

import static org.assertj.core.api.Assertions.assertThat;

import com.dearus.auth.User;
import com.dearus.auth.UserRepository;
import com.dearus.common.CryptoService;
import com.dearus.common.Kst;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 진짜 DB(server/.env의 Supabase)에서 일기 본문이 암호화되어 저장되는지(V9) 확인하는 시험이에요.
 * DB를 건드려서 DEARUS_DB_TEST=true일 때만 돌고, 만든 사용자는 끝나면 지워요.
 * 돌리기: DEARUS_DB_TEST=true ./gradlew test --tests '*DiaryDbTest' -i
 */
@EnabledIfEnvironmentVariable(named = "DEARUS_DB_TEST", matches = "true")
@SpringBootTest(properties = "app.ai.provider=fake")
class DiaryDbTest {

	@Autowired UserRepository users;
	@Autowired DiaryRepository diaries;
	@Autowired CryptoService crypto;
	@Autowired JdbcTemplate jdbc;

	@Test
	void 일기_본문은_DB에_암호화되어_저장되고_읽을_때는_평문이에요() {
		UUID userId = users.saveAndFlush(User.create("dev", "dbtest-" + UUID.randomUUID(), "시험")).getId();
		try {
			LocalDate day = Kst.today().minusDays(3);
			String text = "오늘은 병원에 다녀왔어요. 마음이 많이 힘들었어요";
			UUID id = diaries.saveAndFlush(Diary.create(userId, day, text, Set.of(Emotion.SAD))).getId();

			// 평문 컬럼은 없어지고, 암호화 컬럼에는 원문 글자가 들어 있지 않아요
			Integer plainColumns = jdbc.queryForObject(
				"select count(*) from information_schema.columns where table_name = 'diaries' and column_name = 'content'", Integer.class);
			assertThat(plainColumns).isZero();
			byte[] stored = jdbc.queryForObject("select content_enc from diaries where id = ?", byte[].class, id);
			assertThat(new String(stored, StandardCharsets.UTF_8)).doesNotContain("병원");
			assertThat(crypto.decrypt(stored)).isEqualTo(text);

			// 엔티티로 읽으면 평문이고, 고치면 다시 암호화해 저장해요
			Diary read = diaries.findByIdAndUserId(id, userId).orElseThrow();
			assertThat(read.getContent()).isEqualTo(text);
			read.update("고친 일기예요", Set.of(Emotion.CALM));
			diaries.saveAndFlush(read);
			byte[] updated = jdbc.queryForObject("select content_enc from diaries where id = ?", byte[].class, id);
			assertThat(crypto.decrypt(updated)).isEqualTo("고친 일기예요");
			assertThat(diaries.findByUserIdAndDiaryDate(userId, day).orElseThrow().getContent()).isEqualTo("고친 일기예요");
		} finally {
			users.deleteById(userId);
		}
	}
}
