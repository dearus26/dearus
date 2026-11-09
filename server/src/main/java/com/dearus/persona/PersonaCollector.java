package com.dearus.persona;

import com.dearus.common.Kst;
import com.dearus.diary.Diary;
import com.dearus.diary.DiaryRepository;
import com.dearus.persona.PersonaInput.DiaryEntry;
import com.dearus.persona.PersonaInput.OriginalEntry;
import com.dearus.persona.PersonaInput.WeekEntry;
import com.dearus.upload.MessengerUploadRepository;
import com.dearus.upload.MessengerWeeklySummary;
import com.dearus.upload.MessengerWeeklySummaryRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 고른 구간의 일기, 메신저 주간 요약, 그 요약을 만든 업로드의 내 말 원문을 DB에서 모아요. DB만 읽어서 빨라요(AI를 부르지 않아요). */
@Component
public class PersonaCollector {

	private final DiaryRepository diaries;
	private final MessengerWeeklySummaryRepository summaries;
	private final MessengerUploadRepository uploads;

	public PersonaCollector(DiaryRepository diaries, MessengerWeeklySummaryRepository summaries, MessengerUploadRepository uploads) {
		this.diaries = diaries;
		this.summaries = summaries;
		this.uploads = uploads;
	}

	@Transactional(readOnly = true)
	public PersonaInput collect(UUID userId, PersonaRange range) {
		List<DiaryEntry> diaryEntries = new ArrayList<>();
		for (Diary diary : diaries.findByUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(userId, range.start(), range.end())) {
			diaryEntries.add(new DiaryEntry(diary.getId(), diary.getDiaryDate(), diary.getUpdatedAt(),
				diary.getEmotions().stream().sorted().toList(), diary.getContent()));
		}

		// 같은 주를 여러 번 올렸으면 최근 요약 하나만 써요 (조회가 주 오름차순, 같은 주는 최근 순이라 처음 나온 것이 최근)
		Map<LocalDate, WeekEntry> byWeek = new LinkedHashMap<>();
		Map<UUID, Set<LocalDate>> weeksByUpload = new LinkedHashMap<>();
		for (MessengerWeeklySummary row : summaries.findUsable(userId, range.weekFrom(), range.weekTo(), Kst.now())) {
			if (byWeek.putIfAbsent(row.getWeekStart(), new WeekEntry(row.getId(), row.getWeekStart(), row.getCreatedAt(),
				row.getSummaryEnc(), row.getTraitsEnc(), row.getSampleEnc())) == null) {
				weeksByUpload.computeIfAbsent(row.getUploadId(), k -> new TreeSet<>()).add(row.getWeekStart());
			}
		}

		// 원문은 고른 주를 만든 업로드에서만 가져와요. V7 이전 업로드처럼 원문이 없으면 건너뛰어요.
		List<OriginalEntry> originals = new ArrayList<>();
		weeksByUpload.forEach((uploadId, weekStarts) -> {
			byte[] originalEnc = uploads.findOriginal(uploadId);
			if (originalEnc != null) {
				originals.add(new OriginalEntry(uploadId, Set.copyOf(weekStarts), originalEnc));
			}
		});
		return new PersonaInput(range, List.copyOf(diaryEntries), List.copyOf(byWeek.values()), List.copyOf(originals));
	}
}
