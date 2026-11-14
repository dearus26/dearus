package com.dearus.upload;

import com.dearus.common.CryptoService;
import com.dearus.common.ai.AiException;
import com.dearus.upload.WeeklySummarizer.DatedText;
import com.dearus.upload.WeeklySummarizer.SummaryParts;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 받은 글자를 주 단위로 요약해서 암호화해 저장하는 일이에요. 모든 주의 요약이 끝나면 요약과 내 말 원문(암호화)을
 * 한 트랜잭션에서 저장하고 DONE으로 바꿔요(원문은 업로드와 함께 1년 보관해요).
 * 일부 주만 성공한 채로 저장하지 않고, 하나라도 실패하면 전체를 FAILED로 바꾸고 원문도 남기지 않아요(앱이 같은 글자를 다시 보내요).
 * 로그에는 글자도 요약도 남기지 않고 업로드 ID와 개수만 남겨요.
 */
@Component
public class MessengerSummaryJob {

	private static final Logger log = LoggerFactory.getLogger(MessengerSummaryJob.class);

	private final WeeklySummarizer summarizer;
	private final CryptoService crypto;
	private final MessengerUploadRepository uploads;
	private final MessengerWeeklySummaryRepository summaries;
	private final TransactionTemplate tx;
	private final Executor aiCalls;

	public MessengerSummaryJob(WeeklySummarizer summarizer, CryptoService crypto, MessengerUploadRepository uploads,
		MessengerWeeklySummaryRepository summaries, TransactionTemplate tx, @Qualifier("summaryCallExecutor") Executor aiCalls) {
		this.summarizer = summarizer;
		this.crypto = crypto;
		this.uploads = uploads;
		this.summaries = summaries;
		this.tx = tx;
		this.aiCalls = aiCalls;
	}

	public void run(UUID uploadId, List<WeekMessages> weeks, LocalDate periodStart, LocalDate periodEnd) {
		try {
			List<SummaryParts> results = summarizeAll(weeks);
			tx.executeWithoutResult(status -> save(uploadId, weeks, results, periodStart, periodEnd));
		} catch (AiException e) {
			// e.getMessage()는 종류와 HTTP 상태만 담고 사용자의 글은 담지 않아요
			log.warn("메신저 요약 실패 uploadId={} reason={} detail={}", uploadId, e.reason(), e.getMessage());
			fail(uploadId, e.reason().code());
		} catch (RuntimeException e) {
			log.error("메신저 요약 중 예상하지 못한 오류 uploadId={}", uploadId, e);
			fail(uploadId, "INTERNAL_ERROR");
		}
	}

	/**
	 * 주마다 AI를 한 번씩 부르되 동시에 여러 주를 해요(호출 하나가 5~10초라 차례로 하면 8주에 1분이 넘어요).
	 * 결과는 주 순서대로 돌려주고, 한 주라도 실패하면 아직 시작하지 않은 주는 건너뛰고 그 오류를 그대로 던져요.
	 */
	private List<SummaryParts> summarizeAll(List<WeekMessages> weeks) {
		List<CompletableFuture<SummaryParts>> futures = weeks.stream()
			.map(week -> CompletableFuture.supplyAsync(() -> summarizer.summarize(week), aiCalls))
			.toList();
		List<SummaryParts> results = new ArrayList<>();
		try {
			for (CompletableFuture<SummaryParts> future : futures) {
				results.add(future.join());
			}
		} catch (CompletionException e) {
			futures.forEach(future -> future.cancel(true));
			if (e.getCause() instanceof RuntimeException cause) {
				throw cause;
			}
			throw e;
		}
		return results;
	}

	private void save(UUID uploadId, List<WeekMessages> weeks, List<SummaryParts> results, LocalDate periodStart,
		LocalDate periodEnd) {
		// 요약하는 동안 사용자가 지웠거나 동의를 철회했으면 저장하지 않아요
		MessengerUpload upload = uploads.findById(uploadId).orElse(null);
		if (upload == null) {
			log.info("요약이 끝났지만 업로드가 이미 지워졌어요 uploadId={}", uploadId);
			return;
		}
		for (int i = 0; i < weeks.size(); i++) {
			SummaryParts parts = results.get(i);
			summaries.save(MessengerWeeklySummary.create(
				uploadId,
				weeks.get(i).weekStart(),
				crypto.encrypt(parts.summary()),
				crypto.encrypt(parts.traits()),
				crypto.encrypt(WeeklySummarizer.samplesJson(parts.samples())),
				upload.getExpiresAt()));
		}
		List<DatedText> original = weeks.stream().flatMap(week -> week.lines().stream()).toList();
		uploads.storeOriginal(uploadId, crypto.encrypt(MessengerOriginal.toJson(original)));
		upload.complete(periodStart, periodEnd);
		uploads.save(upload);
		log.info("메신저 요약 완료 uploadId={} weeks={}", uploadId, weeks.size());
	}

	private void fail(UUID uploadId, String failureCode) {
		try {
			tx.executeWithoutResult(status -> uploads.findById(uploadId).ifPresent(upload -> {
				if (upload.isProcessing()) {
					upload.fail(failureCode);
					uploads.save(upload);
				}
			}));
		} catch (RuntimeException e) {
			// 여기서도 실패하면 조회할 때 10분이 지난 PROCESSING을 FAILED로 바꿔요
			log.error("실패 상태를 저장하지 못했어요 uploadId={}", uploadId, e);
		}
	}
}
