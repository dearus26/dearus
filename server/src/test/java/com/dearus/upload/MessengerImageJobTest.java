package com.dearus.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dearus.common.ai.AiClient;
import com.dearus.common.ai.AiException;
import com.dearus.common.ai.AiImage;
import com.dearus.common.ai.AiResult;
import com.dearus.upload.ScreenshotReader.Item;
import com.dearus.upload.WeeklySummarizer.DatedText;
import com.dearus.upload.WeeklySummarizer.WeekMessages;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 스크린샷 업로드의 뒤 작업(날짜 붙이기 · 장 사이 겹침 · 실패 코드 · 사진 버리기)을 실제 AI 없이 확인해요. */
class MessengerImageJobTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);
	private static final LocalDate D1 = LocalDate.of(2026, 9, 28);
	private static final LocalDate D2 = LocalDate.of(2026, 9, 29);

	private final AiClient ai = mock(AiClient.class);
	private final MessengerSummaryJob summaryJob = mock(MessengerSummaryJob.class);
	private final Executor direct = Runnable::run;
	private final MessengerImageJob job = new MessengerImageJob(new ScreenshotReader(ai), summaryJob, direct);
	private final UUID uploadId = UUID.randomUUID();

	private static List<AiImage> images(int count) {
		List<AiImage> images = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			images.add(new AiImage(AiImage.MediaType.PNG, new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, (byte) i}));
		}
		return images;
	}

	@Test
	void 날짜_구분선의_날짜를_뒤따르는_내_말에_붙이고_첫_날짜보다_앞의_말은_빼요() {
		MessengerImageJob.Dated dated = MessengerImageJob.assignDates(List.of(
			Item.mine("날짜를 모르는 말"), Item.dateLine(D1), Item.mine("첫째 날"), Item.mine("또 첫째 날"), Item.dateLine(D2), Item.mine("둘째 날")),
			TODAY);

		assertThat(dated.failureCode()).isNull();
		assertThat(dated.lines()).containsExactly(new DatedText(D1, "첫째 날"), new DatedText(D1, "또 첫째 날"), new DatedText(D2, "둘째 날"));
	}

	@Test
	void 오늘보다_뒤의_날짜에_붙은_말은_잘못_읽은_것으로_보고_빼요() {
		MessengerImageJob.Dated dated = MessengerImageJob.assignDates(List.of(
			Item.dateLine(D1), Item.mine("진짜 말"), Item.dateLine(TODAY.plusYears(1)), Item.mine("미래 날짜의 말")), TODAY);

		assertThat(dated.lines()).containsExactly(new DatedText(D1, "진짜 말"));
	}

	@Test
	void 내_말이_없으면_NO_MESSAGES_FOUND_날짜를_붙인_말이_없으면_DATE_NOT_FOUND예요() {
		assertThat(MessengerImageJob.assignDates(List.of(), TODAY).failureCode()).isEqualTo("NO_MESSAGES_FOUND");
		assertThat(MessengerImageJob.assignDates(List.of(Item.dateLine(D1)), TODAY).failureCode()).isEqualTo("NO_MESSAGES_FOUND");
		assertThat(MessengerImageJob.assignDates(List.of(Item.mine("날짜가 없어요")), TODAY).failureCode()).isEqualTo("DATE_NOT_FOUND");
		assertThat(MessengerImageJob.assignDates(List.of(Item.mine("앞의 말"), Item.dateLine(D1)), TODAY).failureCode())
			.isEqualTo("DATE_NOT_FOUND");
	}

	@Test
	void 묶음_사이에_겹쳐_찍힌_말은_한_번만_남겨요() {
		List<Item> first = List.of(Item.dateLine(D1), Item.mine("오늘 수업 몇 시에 끝나?"), Item.mine("나 도서관 갈 건데"));
		List<Item> second = List.of(Item.mine("오늘 수업 몇 시에 끝나?"), Item.mine("나 도서관 갈 건데"), Item.mine("같이 갈래?"));

		List<Item> stitched = MessengerImageJob.stitch(List.of(first, second));

		assertThat(stitched).containsExactly(Item.dateLine(D1), Item.mine("오늘 수업 몇 시에 끝나?"), Item.mine("나 도서관 갈 건데"),
			Item.mine("같이 갈래?"));
	}

	@Test
	void 우연히_같은_짧은_말은_겹침으로_보지_않아요() {
		List<Item> first = List.of(Item.dateLine(D1), Item.mine("ㅋㅋ"));
		List<Item> second = List.of(Item.mine("ㅋㅋ"), Item.mine("그러게"));

		List<Item> stitched = MessengerImageJob.stitch(List.of(first, second));

		assertThat(stitched).containsExactly(Item.dateLine(D1), Item.mine("ㅋㅋ"), Item.mine("ㅋㅋ"), Item.mine("그러게"));
	}

	@Test
	void 주가_60주를_넘으면_가장_최근_주만_남기고_시작일을_다시_정해요() {
		List<DatedText> lines = new ArrayList<>();
		LocalDate start = LocalDate.of(2025, 1, 6); // 월요일
		for (int week = 0; week < 62; week++) {
			lines.add(new DatedText(start.plusWeeks(week), "말 " + week));
		}
		MessengerService.Grouped grouped = MessengerService.groupByWeek(lines);

		MessengerService.Grouped kept = MessengerImageJob.latestWeeks(grouped, 60);

		assertThat(kept.weeks()).hasSize(60);
		assertThat(kept.first()).isEqualTo(start.plusWeeks(2));
		assertThat(kept.last()).isEqualTo(start.plusWeeks(61));
	}

	@Test
	@SuppressWarnings("unchecked")
	void 읽은_말을_주_단위로_묶어_요약으로_넘기고_사진은_읽은_직후_버려요() {
		when(ai.complete(any())).thenReturn(new AiResult(
			"{\"items\": [{\"date\": \"2026-09-28\"}, {\"me\": \"월요일 싫어\"}, {\"date\": \"2026-10-06\"}, {\"me\": \"다음 주 월요일\"}]}", 1, 1));
		List<AiImage> images = images(3);

		job.run(uploadId, images);

		assertThat(images).isEmpty();
		ArgumentCaptor<List<WeekMessages>> weeks = ArgumentCaptor.forClass(List.class);
		verify(summaryJob).run(eq(uploadId), weeks.capture(), eq(LocalDate.of(2026, 9, 28)), eq(LocalDate.of(2026, 10, 6)));
		assertThat(weeks.getValue()).extracting(WeekMessages::weekStart).containsExactly(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 5));
		verify(summaryJob, never()).fail(any(), any());
	}

	@Test
	void 읽기가_실패하면_그_코드로_FAILED가_되고_사진도_버려요() {
		when(ai.complete(any())).thenThrow(new AiException(AiException.Reason.UNAVAILABLE, "AI를 부르지 못했어요"));
		List<AiImage> images = images(2);

		job.run(uploadId, images);

		assertThat(images).isEmpty();
		verify(summaryJob).fail(uploadId, "AI_UNAVAILABLE");
		verify(summaryJob, never()).run(any(), anyList(), any(), any());
	}

	@Test
	void 내_말을_하나도_못_읽으면_NO_MESSAGES_FOUND로_FAILED예요() {
		when(ai.complete(any())).thenReturn(new AiResult("{\"items\": [{\"date\": \"2026-09-28\"}]}", 1, 1));

		job.run(uploadId, images(1));

		verify(summaryJob).fail(uploadId, "NO_MESSAGES_FOUND");
		verify(summaryJob, never()).run(any(), anyList(), any(), any());
	}

	@Test
	void 열한_장_이상이면_묶음마다_AI를_부르고_앞_묶음의_날짜를_이어서_써요() {
		when(ai.complete(any()))
			.thenReturn(new AiResult("{\"items\": [{\"date\": \"2026-09-28\"}, {\"me\": \"앞 묶음의 말\"}]}", 1, 1))
			.thenReturn(new AiResult("{\"items\": [{\"me\": \"날짜 구분선 없이 이어진 말\"}]}", 1, 1));

		job.run(uploadId, images(11));

		verify(ai, org.mockito.Mockito.times(2)).complete(any());
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<WeekMessages>> weeks = ArgumentCaptor.forClass(List.class);
		verify(summaryJob).run(eq(uploadId), weeks.capture(), any(), any());
		assertThat(weeks.getValue().get(0).lines()).containsExactly(new DatedText(D1, "앞 묶음의 말"), new DatedText(D1, "날짜 구분선 없이 이어진 말"));
	}
}
