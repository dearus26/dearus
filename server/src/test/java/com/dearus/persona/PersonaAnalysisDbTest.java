package com.dearus.persona;

import static org.assertj.core.api.Assertions.assertThat;

import com.dearus.auth.ConsentPurgeJob;
import com.dearus.auth.ConsentRequest;
import com.dearus.auth.ConsentService;
import com.dearus.auth.User;
import com.dearus.auth.UserRepository;
import com.dearus.common.CryptoService;
import com.dearus.common.Kst;
import com.dearus.common.Weeks;
import com.dearus.diary.Diary;
import com.dearus.diary.DiaryRepository;
import com.dearus.diary.Emotion;
import com.dearus.persona.PersonaAnalysisService.Result;
import com.dearus.upload.MessengerOriginal;
import com.dearus.upload.MessengerSource;
import com.dearus.upload.MessengerUpload;
import com.dearus.upload.MessengerUploadRepository;
import com.dearus.upload.MessengerWeeklySummary;
import com.dearus.upload.MessengerWeeklySummaryRepository;
import com.dearus.upload.WeeklySummarizer.DatedText;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 진짜 DB(server/.env의 Supabase)에 저장 · 조회 · 덮어쓰기 · 철회 삭제가 되는지 확인하는 시험이에요.
 * 동의 철회 뒤 30일 보관 · 재동의 · 폐기도 확인해요.
 * DB를 건드려서 DEARUS_DB_TEST=true일 때만 돌고, 가짜 AI(app.ai.provider=fake)를 쓰며, 만든 사용자는 끝나면 지워요.
 * 돌리기: DEARUS_DB_TEST=true ./gradlew test --tests '*PersonaAnalysisDbTest' -i
 */
@EnabledIfEnvironmentVariable(named = "DEARUS_DB_TEST", matches = "true")
@SpringBootTest(properties = "app.ai.provider=fake")
class PersonaAnalysisDbTest {

	@Autowired UserRepository users;
	@Autowired DiaryRepository diaries;
	@Autowired MessengerUploadRepository uploads;
	@Autowired MessengerWeeklySummaryRepository summaries;
	@Autowired PersonaAnalysisRepository analyses;
	@Autowired PersonaAnalysisService service;
	@Autowired PersonaWindowService windows;
	@Autowired ConsentService consent;
	@Autowired ConsentPurgeJob purge;
	@Autowired CryptoService crypto;
	@Autowired PersonaCollector collector;
	@Autowired TransactionTemplate tx;

	@Test
	void 분석을_저장하고_재사용하고_덮어쓰고_철회_뒤_30일이_지나면_지워요() {
		User user = User.create("dev", "dbtest-" + UUID.randomUUID(), "시험");
		user.agree(false);
		UUID userId = users.saveAndFlush(user).getId();
		try {
			LocalDate anchor = Weeks.startOf(Kst.today().minusDays(60));
			List<Diary> written = new ArrayList<>();
			for (int i = 0; i < 3; i++) {
				written.add(diaries.saveAndFlush(Diary.create(userId, anchor.plusDays(i), "일기 " + i + " 내 번호 010-1234-5678", Set.of(Emotion.TIRED))));
			}
			MessengerUpload upload = MessengerUpload.receive(userId, MessengerSource.KAKAOTALK, 0);
			upload.complete(anchor, anchor);
			upload = uploads.saveAndFlush(upload);
			summaries.saveAndFlush(MessengerWeeklySummary.create(upload.getId(), anchor, crypto.encrypt("요약"), crypto.encrypt("짧은 반말"),
				crypto.encrypt("[\"오늘 피곤\"]"), upload.getExpiresAt()));
			// 내 말 원문(V7의 original_enc)은 엔티티에 매핑하지 않아서 전용 쿼리로 쓰고 읽어요
			UUID uploadId = upload.getId();
			assertThat(uploads.findOriginal(uploadId)).isNull(); // 요약 전에는 비어 있어요
			byte[] originalEnc = crypto.encrypt(MessengerOriginal.toJson(List.of(
				new DatedText(anchor, "월요일 한 말"), new DatedText(anchor.plusDays(1), "화요일 한 말"))));
			tx.executeWithoutResult(status -> uploads.storeOriginal(uploadId, originalEnc));
			assertThat(MessengerOriginal.fromJson(crypto.decrypt(uploads.findOriginal(uploadId))))
				.extracting(DatedText::text).containsExactly("월요일 한 말", "화요일 한 말");
			assertThat(uploads.findById(uploadId).orElseThrow().getStatus()).isEqualTo(upload.getStatus()); // 다른 컬럼은 그대로

			// 시점 분석은 고른 주의 업로드 원문을 모아요. 하루 단위는 그날의 말만 써요
			PersonaInput aroundInput = collector.collect(userId, PersonaRange.of(PersonaScope.AROUND, anchor, Kst.today()));
			assertThat(aroundInput.originals()).extracting(PersonaInput.OriginalEntry::uploadId).containsExactly(uploadId);
			assertThat(service.rawLines(aroundInput)).extracting(DatedText::text).containsExactly("월요일 한 말", "화요일 한 말");
			PersonaInput dayInput = collector.collect(userId, PersonaRange.of(PersonaScope.DAY, anchor, Kst.today()));
			assertThat(service.rawLines(dayInput)).extracting(DatedText::text).containsExactly("월요일 한 말");

			// 고른 구간의 기록량(DB에서 모아요): 일기 3건 + 겹치는 메신저 1주
			PersonaWindowResponse window = windows.window(userId, PersonaScope.AROUND, anchor);
			assertThat(window.diaryCount()).isEqualTo(3);
			assertThat(window.messengerWeeks()).isEqualTo(1);
			assertThat(window.level()).isEqualTo(RecordLevel.ENOUGH);
			PersonaWindowResponse day = windows.window(userId, PersonaScope.DAY, anchor);
			assertThat(day.diaryCount()).isEqualTo(1);
			assertThat(day.messengerWeeks()).isEqualTo(1); // 그날이 속한 주(= anchor의 주)의 말투 분석
			assertThat(day.emotions()).containsExactly(Emotion.TIRED);

			// 처음에는 분석해서 저장
			List<PersonaAnalysisService.Step> steps = new ArrayList<>();
			Result first = service.getOrCreate(userId, PersonaScope.AROUND, anchor, steps::add).orElseThrow();
			assertThat(first.reused()).isFalse();
			assertThat(steps).containsExactly(PersonaAnalysisService.Step.COLLECT,
				PersonaAnalysisService.Step.ANALYZE, PersonaAnalysisService.Step.BUILD);
			PersonaAnalysis row = analyses.findById(first.analysisId()).orElseThrow();
			assertThat(row.getWindowDays()).isEqualTo((short) 14);
			assertThat(row.getDiaryCount()).isEqualTo(3);
			assertThat(row.getMessengerWeeks()).isEqualTo(1);
			assertThat(PersonaContent.fromJson(crypto.decrypt(row.getAnalysisEnc()))).isEqualTo(first.content());
			String firstDigest = row.getSourceDigest();

			// 같은 기록이면 재사용
			Result second = service.getOrCreate(userId, PersonaScope.AROUND, anchor, step -> { }).orElseThrow();
			assertThat(second.reused()).isTrue();
			assertThat(second.analysisId()).isEqualTo(first.analysisId());

			// 일기를 고치면 같은 행을 덮어써요
			Diary edited = diaries.findByIdAndUserId(written.get(0).getId(), userId).orElseThrow();
			edited.update("고친 일기", Set.of(Emotion.JOY));
			diaries.saveAndFlush(edited);
			Result third = service.getOrCreate(userId, PersonaScope.AROUND, anchor, step -> { }).orElseThrow();
			assertThat(third.reused()).isFalse();
			assertThat(third.analysisId()).isEqualTo(first.analysisId());
			assertThat(analyses.findById(first.analysisId()).orElseThrow().getSourceDigest()).isNotEqualTo(firstDigest);

			// 하루 단위는 따로 한 행(window_days = 0)
			Result dayResult = service.getOrCreate(userId, PersonaScope.DAY, anchor, step -> { }).orElseThrow();
			assertThat(dayResult.analysisId()).isNotEqualTo(first.analysisId());
			assertThat(analyses.findById(dayResult.analysisId()).orElseThrow().getWindowDays()).isZero();

			// 동의를 철회해도 바로 지우지 않고 30일 보관해요
			consent.revokeAiConsent(userId);
			assertThat(users.findById(userId).orElseThrow().aiConsentActive()).isFalse(); // 철회가 저장됐는지
			assertThat(analyses.findById(first.analysisId())).isPresent();
			assertThat(analyses.findById(dayResult.analysisId())).isPresent();
			assertThat(uploads.findByUserIdOrderByUploadedAtDesc(userId)).hasSize(1);
			assertThat(purge.purgeIfExpired(userId, Kst.now())).isFalse(); // 보관 기간 안이에요

			// 그 안에 다시 동의하면 보관하던 분석을 그대로 이어서 써요
			consent.agree(userId, new ConsentRequest(true, true, true, null));
			Result restored = service.getOrCreate(userId, PersonaScope.AROUND, anchor, step -> { }).orElseThrow();
			assertThat(restored.reused()).isTrue();
			assertThat(restored.analysisId()).isEqualTo(first.analysisId());

			// 다시 철회하고 30일이 지나면 분석과 메신저 요약이 지워지고, 일기는 그대로예요
			consent.revokeAiConsent(userId);
			User revoked = users.findById(userId).orElseThrow();
			ReflectionTestUtils.setField(revoked, "aiRevokedAt", Kst.now().minusDays(31));
			users.saveAndFlush(revoked);
			assertThat(purge.purgeIfExpired(userId, Kst.now())).isTrue();
			assertThat(analyses.findById(first.analysisId())).isEmpty();
			assertThat(analyses.findById(dayResult.analysisId())).isEmpty();
			assertThat(uploads.findByUserIdOrderByUploadedAtDesc(userId)).isEmpty();
			assertThat(summaries.findByUploadIdOrderByWeekStartAsc(upload.getId())).isEmpty();
			assertThat(uploads.findOriginal(uploadId)).isNull(); // 원문도 업로드 행과 함께 지워졌어요
			assertThat(diaries.findByUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(userId, anchor, anchor.plusDays(2))).hasSize(3);
		} finally {
			users.deleteById(userId); // 일기 · 업로드 · 요약 · 분석이 ON DELETE CASCADE로 함께 지워져요
		}
	}
}
