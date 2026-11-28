package com.dearus.diary;

import static org.assertj.core.api.Assertions.assertThat;

import com.dearus.auth.User;
import com.dearus.auth.UserRepository;
import com.dearus.common.CryptoService;
import com.dearus.common.Kst;
import com.dearus.upload.MessengerDayNote;
import com.dearus.upload.MessengerDayNoteRepository;
import com.dearus.upload.MessengerOriginal;
import com.dearus.upload.MessengerSource;
import com.dearus.upload.MessengerUpload;
import com.dearus.upload.MessengerUploadRepository;
import com.dearus.upload.WeeklySummarizer.DatedText;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 진짜 DB(server/.env의 Supabase)에서 홈 캘린더(GET /calendar, V13 messenger_day_notes)가 일기 날과 메신저 날을 함께 주는지 확인해요(2026.11.28).
 * DB를 건드려서 DEARUS_DB_TEST=true일 때만 돌고, 가짜 AI를 쓰며, 만든 사용자는 끝나면 지워요(업로드 · 하루 기록도 함께 지워져요).
 * 돌리기: DEARUS_DB_TEST=true ./gradlew test --tests '*CalendarDbTest' -i
 */
@EnabledIfEnvironmentVariable(named = "DEARUS_DB_TEST", matches = "true")
@SpringBootTest(properties = "app.ai.provider=fake")
class CalendarDbTest {

	@Autowired UserRepository users;
	@Autowired DiaryRepository diaries;
	@Autowired MessengerUploadRepository uploads;
	@Autowired MessengerDayNoteRepository dayNotes;
	@Autowired CalendarService calendar;
	@Autowired CryptoService crypto;
	@Autowired TransactionTemplate tx;

	@Test
	void 일기_날과_메신저_날을_추천_감정_한_줄_요약과_함께_줘요() {
		User user = User.create("dev", "dbtest-" + UUID.randomUUID(), "시험");
		UUID userId = users.saveAndFlush(user).getId();
		try {
			LocalDate d1 = Kst.today().minusDays(10);
			LocalDate d2 = d1.plusDays(1);
			diaries.saveAndFlush(Diary.create(userId, d1, "일기", Set.of(Emotion.CALM)));
			MessengerUpload upload = MessengerUpload.receive(userId, MessengerSource.KAKAOTALK, 0);
			upload.complete(d2, d2);
			UUID uploadId = uploads.saveAndFlush(upload).getId();
			byte[] original = crypto.encrypt(MessengerOriginal.toJson(List.of(new DatedText(d2, "MT 짐 싸는 중"))));
			tx.executeWithoutResult(status -> uploads.storeOriginal(uploadId, original));
			dayNotes.saveAndFlush(MessengerDayNote.create(uploadId, d2, List.of(Emotion.EXCITED, Emotion.ANXIOUS),
				crypto.encrypt("MT 짐을 쌌어요.")));

			List<CalendarDayResponse> items = calendar.days(userId, d1.minusDays(1), d2.plusDays(1)).items();

			assertThat(items).containsExactly(
				new CalendarDayResponse(d1, true, List.of(Emotion.CALM), false, List.of(), null),
				new CalendarDayResponse(d2, false, List.of(), true, List.of(Emotion.EXCITED, Emotion.ANXIOUS), "MT 짐을 쌌어요."));

			// 업로드를 지우면 하루 기록도 함께 지워져요(ON DELETE CASCADE)
			uploads.deleteById(uploadId);
			uploads.flush();
			assertThat(dayNotes.findByUploadIdInAndDayBetween(Set.of(uploadId), d1, d2)).isEmpty();
		} finally {
			users.deleteById(userId);
		}
	}
}
