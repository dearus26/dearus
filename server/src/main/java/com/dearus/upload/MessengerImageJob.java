package com.dearus.upload;

import com.dearus.common.Kst;
import com.dearus.common.ai.AiException;
import com.dearus.common.ai.AiImage;
import com.dearus.upload.ScreenshotReader.Item;
import com.dearus.upload.WeeklySummarizer.DatedText;
import com.dearus.upload.WeeklySummarizer.WeekMessages;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 스크린샷으로 올린 업로드를 뒤에서 처리해요(POST /uploads/messenger/images, 2026.11.8 결정).
 * 1) 스크린샷을 묶음으로 나눠 AI로 읽어요(여러 묶음을 동시에). 2) 읽기가 끝나면 성공이든 실패든 사진을 바로 버려요.
 * 3) 날짜 구분선의 날짜를 뒤따르는 내 말에 붙여요(첫 날짜보다 앞의 말은 날짜를 알 수 없어서 빼요). 4) 주 단위로 묶어
 * txt와 같은 요약 작업(MessengerSummaryJob)에 넘겨요. 그래서 원문 저장 · 요약 · DONE/FAILED 규칙이 txt와 같아요.
 * 사진은 처음부터 디스크 · DB에 쓰지 않고(멀티파트도 메모리에서만 받아요) 이 작업이 들고 있던 목록을 비우면 메모리에서도 놓아요.
 * 로그에는 사진도 글자도 남기지 않고 업로드 ID와 개수만 남겨요.
 */
@Component
public class MessengerImageJob {

	private static final Logger log = LoggerFactory.getLogger(MessengerImageJob.class);

	/** 묶음 사이에서 겹친 항목을 찾을 때 볼 최대 항목 수예요. */
	static final int MAX_OVERLAP = 30;
	/** 겹침으로 볼 최소 글자 수예요. "ㅋㅋ" 하나처럼 짧은 말이 우연히 같은 것은 겹침으로 보지 않아요. */
	static final int MIN_OVERLAP_CHARS = 10;

	/** 날짜를 붙인 결과예요. failureCode가 있으면 실패이고 lines는 비어 있어요. */
	record Dated(List<DatedText> lines, String failureCode) {
	}

	private final ScreenshotReader reader;
	private final MessengerSummaryJob summaryJob;
	private final Executor aiCalls;

	public MessengerImageJob(ScreenshotReader reader, MessengerSummaryJob summaryJob, @Qualifier("summaryCallExecutor") Executor aiCalls) {
		this.reader = reader;
		this.summaryJob = summaryJob;
		this.aiCalls = aiCalls;
	}

	/** images는 대화 순서대로이고 바꿀 수 있는 목록이어야 해요(읽은 직후 비워요). */
	public void run(UUID uploadId, List<AiImage> images) {
		int photos = images.size();
		List<Item> items;
		try {
			items = readAll(images);
		} catch (AiException e) {
			// e.getMessage()는 종류와 HTTP 상태만 담고 사진 속 글자는 담지 않아요
			log.warn("스크린샷 읽기 실패 uploadId={} photos={} reason={} detail={}", uploadId, photos, e.reason(), e.getMessage());
			summaryJob.fail(uploadId, e.reason().code());
			return;
		} catch (RuntimeException e) {
			log.error("스크린샷 읽기 중 예상하지 못한 오류 uploadId={} photos={}", uploadId, photos, e);
			summaryJob.fail(uploadId, "INTERNAL_ERROR");
			return;
		} finally {
			images.clear(); // 사진은 읽은 직후 지워요
		}

		Dated dated = assignDates(items, Kst.today());
		if (dated.failureCode() != null) {
			log.info("스크린샷에서 쓸 수 있는 내 말이 없어요 uploadId={} photos={} items={} code={}", uploadId, photos, items.size(),
				dated.failureCode());
			summaryJob.fail(uploadId, dated.failureCode());
			return;
		}
		MessengerService.Grouped grouped = latestWeeks(MessengerService.groupByWeek(dated.lines()), MessengerService.MAX_WEEKS);
		log.info("스크린샷 읽기 완료 uploadId={} photos={} lines={} weeks={}", uploadId, photos, dated.lines().size(), grouped.weeks().size());
		summaryJob.run(uploadId, grouped.weeks(), grouped.first(), grouped.last());
	}

	/**
	 * 묶음마다 AI를 한 번씩 부르되 동시에 여러 묶음을 해요(전체 동시 호출 수는 summaryCallExecutor가 묶어요).
	 * 결과는 묶음 순서대로 이어 붙이고, 한 묶음이라도 실패하면 아직 시작하지 않은 묶음은 건너뛰고 그 오류를 던져요.
	 */
	private List<Item> readAll(List<AiImage> images) {
		List<CompletableFuture<List<Item>>> futures = ScreenshotReader.batches(images).stream()
			.map(batch -> CompletableFuture.supplyAsync(() -> reader.read(batch), aiCalls))
			.toList();
		List<List<Item>> results = new ArrayList<>();
		try {
			for (CompletableFuture<List<Item>> future : futures) {
				results.add(future.join());
			}
		} catch (CompletionException e) {
			futures.forEach(future -> future.cancel(true));
			if (e.getCause() instanceof RuntimeException cause) {
				throw cause;
			}
			throw e;
		}
		return stitch(results);
	}

	/**
	 * 묶음별로 읽은 결과를 순서대로 이어 붙여요. 스크롤하며 찍은 스크린샷은 장 사이가 겹쳐서, 앞 묶음의 끝과 다음 묶음의 앞이
	 * 같은 항목으로 이어지면 그만큼 한 번만 남겨요(한 묶음 안의 겹침은 AI가 한 번만 적어요).
	 */
	static List<Item> stitch(List<List<Item>> batches) {
		List<Item> out = new ArrayList<>();
		for (List<Item> batch : batches) {
			int skip = overlap(out, batch);
			out.addAll(batch.subList(skip, batch.size()));
		}
		return out;
	}

	/** before의 끝과 next의 앞이 겹치는 가장 긴 길이예요. 겹친 부분의 내 말이 MIN_OVERLAP_CHARS자 이상일 때만 겹침으로 봐요. */
	static int overlap(List<Item> before, List<Item> next) {
		int max = Math.min(MAX_OVERLAP, Math.min(before.size(), next.size()));
		for (int k = max; k >= 1; k--) {
			List<Item> head = next.subList(0, k);
			if (before.subList(before.size() - k, before.size()).equals(head) && mineChars(head) >= MIN_OVERLAP_CHARS) {
				return k;
			}
		}
		return 0;
	}

	private static int mineChars(List<Item> items) {
		return items.stream().filter(item -> !item.isDate()).mapToInt(item -> item.text().length()).sum();
	}

	/**
	 * 읽은 순서대로 날짜 구분선의 날짜를 뒤따르는 내 말에 붙여요. 첫 날짜 구분선보다 앞의 말은 날짜를 알 수 없어서 빼고,
	 * 오늘보다 뒤의 날짜(잘못 읽은 날짜)에 붙은 말도 빼요.
	 * 내 말이 하나도 없으면 NO_MESSAGES_FOUND, 내 말은 있지만 날짜를 붙인 말이 하나도 없으면 DATE_NOT_FOUND예요.
	 */
	static Dated assignDates(List<Item> items, LocalDate today) {
		boolean sawMine = false;
		LocalDate current = null;
		List<DatedText> lines = new ArrayList<>();
		for (Item item : items) {
			if (item.isDate()) {
				current = item.date();
				continue;
			}
			sawMine = true;
			if (current != null && !current.isAfter(today)) {
				lines.add(new DatedText(current, item.text()));
			}
		}
		if (!sawMine) {
			return new Dated(List.of(), "NO_MESSAGES_FOUND");
		}
		if (lines.isEmpty()) {
			return new Dated(List.of(), "DATE_NOT_FOUND");
		}
		return new Dated(lines, null);
	}

	/**
	 * 한 업로드에서 요약하는 주는 MAX_WEEKS(약 1년)까지예요. txt는 넘으면 400이지만 이미지는 202 뒤에 알게 되어서,
	 * 넘으면 가장 최근 주만 남겨요(2026.11.9, 100장으로 60주를 넘는 경우는 드물어요).
	 */
	static MessengerService.Grouped latestWeeks(MessengerService.Grouped grouped, int maxWeeks) {
		List<WeekMessages> weeks = grouped.weeks();
		if (weeks.size() <= maxWeeks) {
			return grouped;
		}
		List<WeekMessages> kept = List.copyOf(weeks.subList(weeks.size() - maxWeeks, weeks.size()));
		return new MessengerService.Grouped(kept, kept.get(0).lines().get(0).date(), grouped.last());
	}
}
