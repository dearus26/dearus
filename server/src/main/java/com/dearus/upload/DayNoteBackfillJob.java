package com.dearus.upload;

import com.dearus.common.CryptoService;
import com.dearus.common.Kst;
import com.dearus.common.ai.AiException;
import com.dearus.upload.WeeklySummarizer.DatedText;
import com.dearus.upload.WeeklySummarizer.DayNote;
import com.dearus.upload.WeeklySummarizer.WeekMessages;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 2026.11.28 전에 올린 업로드에 날마다의 추천 감정 · 한 줄 요약(messenger_day_notes)을 채우는 일이에요.
 * 서버에 보관한 내 말 원문으로 주마다 요약 AI를 한 번씩 다시 불러 days만 저장해요(주간 요약은 그대로 둬요). 사용자가 다시 올리지 않아도 돼요.
 * 배포 서버가 켜질 때 뒤에서 돌고(StartupJobs), 한 번에 MAX_UPLOADS개까지만 해서 비용이 묶여요. 남은 것은 다음에 켜질 때 이어서 해요.
 * AI 호출 동안 DB 연결을 잡고 있지 않아요(목록 조회 · 저장만 짧은 트랜잭션). 모델이 어떤 날을 빠뜨리면 그날은 빈 기록(감정 없음 · 요약 없음)을 넣어서
 * 같은 업로드를 다시 부르지 않아요. 로그에는 글 없이 업로드 ID와 개수만 남겨요.
 */
@Component
public class DayNoteBackfillJob {

	private static final Logger log = LoggerFactory.getLogger(DayNoteBackfillJob.class);

	/** 한 번 돌 때 채우는 업로드 수(업로드 하나는 주마다 AI 한 번, 하루치 업로드면 한 번이에요) */
	static final int MAX_UPLOADS = 20;

	private final MessengerUploadRepository uploads;
	private final MessengerDayNoteRepository dayNotes;
	private final WeeklySummarizer summarizer;
	private final CryptoService crypto;
	private final TransactionTemplate tx;

	public DayNoteBackfillJob(MessengerUploadRepository uploads, MessengerDayNoteRepository dayNotes, WeeklySummarizer summarizer,
		CryptoService crypto, TransactionTemplate tx) {
		this.uploads = uploads;
		this.dayNotes = dayNotes;
		this.summarizer = summarizer;
		this.crypto = crypto;
		this.tx = tx;
	}

	/** 채운 업로드 수를 돌려줘요. 실패는 로그로 남기고 다음 업로드로 넘어가요(다음에 켜질 때 다시 해요). */
	public int run() {
		List<UUID> ids = uploads.findMissingDayNotes(Kst.now(), MAX_UPLOADS);
		int filled = 0;
		for (UUID uploadId : ids) {
			try {
				if (fill(uploadId)) {
					filled++;
				}
			} catch (AiException e) {
				// e.getMessage()는 종류와 HTTP 상태만 담고 사용자의 글은 담지 않아요
				log.warn("하루 기록 채우기 실패 uploadId={} reason={}", uploadId, e.reason());
			} catch (RuntimeException e) {
				log.error("하루 기록 채우기 중 예상하지 못한 오류 uploadId={}", uploadId, e);
			}
		}
		if (!ids.isEmpty()) {
			log.info("예전 업로드의 하루 기록을 채웠어요 filled={} tried={}", filled, ids.size());
		}
		return filled;
	}

	private boolean fill(UUID uploadId) {
		byte[] originalEnc = uploads.findOriginal(uploadId);
		if (originalEnc == null) {
			return false;
		}
		List<DatedText> lines = MessengerOriginal.fromJson(crypto.decrypt(originalEnc));
		if (lines.isEmpty()) {
			return false;
		}
		// AI를 부르는 동안에는 DB 연결을 잡고 있지 않아요
		Map<LocalDate, DayNote> notes = new LinkedHashMap<>();
		for (WeekMessages week : MessengerService.groupByWeek(lines).weeks()) {
			for (DayNote note : summarizer.summarize(week).days()) {
				notes.putIfAbsent(note.date(), note);
			}
		}
		// 모델이 빠뜨린 날은 빈 기록을 넣어서 다음에 다시 부르지 않아요(캘린더에는 추천 감정 없이 메신저 날로 보여요)
		for (DatedText line : lines) {
			notes.putIfAbsent(line.date(), new DayNote(line.date(), List.of(), null));
		}
		List<DayNote> toSave = new ArrayList<>(notes.values());
		Boolean saved = tx.execute(status -> {
			// 그 사이 지워졌거나 다른 서버가 먼저 채웠으면 건너뛰어요
			if (!uploads.existsById(uploadId) || dayNotes.existsByUploadId(uploadId)) {
				return false;
			}
			for (DayNote note : toSave) {
				dayNotes.save(MessengerDayNote.create(uploadId, note.date(), note.emotions(),
					note.line() == null ? null : crypto.encrypt(note.line())));
			}
			return true;
		});
		return Boolean.TRUE.equals(saved);
	}
}
