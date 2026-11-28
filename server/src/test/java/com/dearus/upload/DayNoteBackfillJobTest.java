package com.dearus.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dearus.common.CryptoProperties;
import com.dearus.common.CryptoService;
import com.dearus.common.ai.AiClient;
import com.dearus.common.ai.AiException;
import com.dearus.common.ai.AiResult;
import com.dearus.diary.Emotion;
import com.dearus.upload.WeeklySummarizer.DatedText;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 2026.11.28 전 업로드에 하루 기록(추천 감정 · 한 줄 요약)을 채우는 일을 DB · 진짜 AI 없이 확인해요. */
class DayNoteBackfillJobTest {

	private final MessengerUploadRepository uploads = mock(MessengerUploadRepository.class);
	private final MessengerDayNoteRepository dayNotes = mock(MessengerDayNoteRepository.class);
	private final AiClient ai = mock(AiClient.class);
	private final CryptoService crypto = new CryptoService(new CryptoProperties(Base64.getEncoder().encodeToString(new byte[32])));
	private final DayNoteBackfillJob job = new DayNoteBackfillJob(uploads, dayNotes, new WeeklySummarizer(ai), crypto,
		new TransactionTemplate(mock(PlatformTransactionManager.class)));
	private final UUID uploadId = UUID.randomUUID();
	private final List<MessengerDayNote> saved = new ArrayList<>();
	private final LocalDate mon = LocalDate.of(2026, 9, 28);

	@BeforeEach
	void setUp() {
		when(uploads.findMissingDayNotes(any(), anyInt())).thenReturn(List.of(uploadId));
		when(uploads.existsById(uploadId)).thenReturn(true);
		when(uploads.findOriginal(uploadId)).thenReturn(crypto.encrypt(MessengerOriginal.toJson(List.of(
			new DatedText(mon, "월요일 피곤"), new DatedText(mon.plusDays(2), "수요일 신남")))));
		when(dayNotes.save(any())).thenAnswer(call -> {
			saved.add(call.getArgument(0));
			return call.getArgument(0);
		});
	}

	@Test
	void 원문으로_다시_요약해_날마다_기록을_채우고_빠진_날은_빈_기록이에요() {
		when(ai.complete(any())).thenReturn(new AiResult("{\"summary\":\"요약\",\"traits\":\"말투\",\"sample\":[\"문장\"],"
			+ "\"days\":[{\"date\":\"09-28\",\"emotions\":[\"TIRED\"],\"line\":\"피곤했어요.\"}]}", 1, 1));

		assertThat(job.run()).isEqualTo(1);

		assertThat(saved).extracting(MessengerDayNote::getDay).containsExactly(mon, mon.plusDays(2));
		assertThat(saved.get(0).emotionList()).containsExactly(Emotion.TIRED);
		assertThat(crypto.decrypt(saved.get(0).getLineEnc())).isEqualTo("피곤했어요.");
		// 모델이 빠뜨린 수요일은 빈 기록이라 다음에 다시 부르지 않아요
		assertThat(saved.get(1).emotionList()).isEmpty();
		assertThat(saved.get(1).getLineEnc()).isNull();
	}

	@Test
	void 그_사이_다른_서버가_채웠으면_건너뛰어요() {
		when(ai.complete(any())).thenReturn(new AiResult("{\"summary\":\"요약\",\"traits\":\"말투\",\"sample\":[\"문장\"]}", 1, 1));
		when(dayNotes.existsByUploadId(uploadId)).thenReturn(true);

		assertThat(job.run()).isZero();
		verify(dayNotes, never()).save(any());
	}

	@Test
	void AI가_실패하면_저장하지_않고_다음에_다시_해요() {
		when(ai.complete(any())).thenThrow(new AiException(AiException.Reason.UNAVAILABLE, "닿지 못했어요"));

		assertThat(job.run()).isZero();
		verify(dayNotes, never()).save(any());
	}
}
