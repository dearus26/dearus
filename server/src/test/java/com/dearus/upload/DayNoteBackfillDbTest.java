package com.dearus.upload;

import static org.assertj.core.api.Assertions.assertThat;

import com.dearus.auth.User;
import com.dearus.auth.UserRepository;
import com.dearus.common.CryptoService;
import com.dearus.common.Kst;
import com.dearus.upload.WeeklySummarizer.DatedText;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 진짜 DB에서 "하루 기록이 아직 없는 업로드" 쿼리(findMissingDayNotes, 2026.11.28)가 맞는지 확인해요.
 * 채우기 작업(DayNoteBackfillJob.run)은 부르지 않아요. 공용 DB의 다른 사람 업로드까지 가짜 AI로 채워 버리면 안 돼서요.
 * DB를 건드려서 DEARUS_DB_TEST=true일 때만 돌고, 만든 사용자는 끝나면 지워요.
 * 돌리기: DEARUS_DB_TEST=true ./gradlew test --tests '*DayNoteBackfillDbTest' -i
 */
@EnabledIfEnvironmentVariable(named = "DEARUS_DB_TEST", matches = "true")
@SpringBootTest(properties = "app.ai.provider=fake")
class DayNoteBackfillDbTest {

	@Autowired UserRepository users;
	@Autowired MessengerUploadRepository uploads;
	@Autowired MessengerDayNoteRepository dayNotes;
	@Autowired CryptoService crypto;
	@Autowired TransactionTemplate tx;

	@Test
	void 하루_기록이_없고_동의가_유효한_업로드만_찾아요() {
		User user = User.create("dev", "dbtest-" + UUID.randomUUID(), "시험");
		user.agree(false); // AI 분석 · 민감정보 동의가 함께 기록돼요
		UUID userId = users.saveAndFlush(user).getId();
		try {
			LocalDate day = Kst.today().minusDays(10);
			MessengerUpload upload = MessengerUpload.receive(userId, MessengerSource.KAKAOTALK, 0);
			upload.complete(day, day);
			UUID uploadId = uploads.saveAndFlush(upload).getId();
			byte[] original = crypto.encrypt(MessengerOriginal.toJson(List.of(new DatedText(day, "그날 한 말"))));
			tx.executeWithoutResult(status -> uploads.storeOriginal(uploadId, original));

			assertThat(uploads.findMissingDayNotes(Kst.now(), 10_000)).contains(uploadId);
			assertThat(dayNotes.existsByUploadId(uploadId)).isFalse();

			dayNotes.saveAndFlush(MessengerDayNote.create(uploadId, day, List.of(), null));

			assertThat(uploads.findMissingDayNotes(Kst.now(), 10_000)).doesNotContain(uploadId);
			assertThat(dayNotes.existsByUploadId(uploadId)).isTrue();
		} finally {
			users.deleteById(userId);
		}
	}
}
