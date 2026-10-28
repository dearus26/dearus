package com.dearus.persona;

import com.dearus.common.Kst;
import com.dearus.diary.Diary;
import com.dearus.diary.DiaryRepository;
import com.dearus.persona.PersonaInput.DiaryEntry;
import com.dearus.persona.PersonaInput.WeekEntry;
import com.dearus.upload.MessengerWeeklySummary;
import com.dearus.upload.MessengerWeeklySummaryRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 고른 구간의 일기와 메신저 주간 요약을 DB에서 모아요. DB만 읽어서 빨라요(AI를 부르지 않아요). */
@Component
public class PersonaCollector {

	private final DiaryRepository diaries;
	private final MessengerWeeklySummaryRepository summaries;

	public PersonaCollector(DiaryRepository diaries, MessengerWeeklySummaryRepository summaries) {
		this.diaries = diaries;
		this.summaries = summaries;
	}

	@Transactional(readOnly = true)
	public PersonaInput collect(UUID userId, PersonaRange range) {
		List<DiaryEntry> diaryEntries = new ArrayList<>();
		for (Diary diary : diaries.findByUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(userId, range.start(), range.end())) {
			diaryEntries.add(new DiaryEntry(diary.getId(), diary.getDiaryDate(), diary.getUpdatedAt(),
				diary.getEmotions().stream().sorted().toList(), diary.getContent()));
		}

		// 같은 주를 여러 번 올렸으면 최근 요약 하나만 써요 (조회가 주 오름차순, 같은 주는 최근 순이라 처음 나온 것이 최근)
		Map<java.time.LocalDate, WeekEntry> byWeek = new LinkedHashMap<>();
		for (MessengerWeeklySummary row : summaries.findUsable(userId, range.weekFrom(), range.weekTo(), Kst.now())) {
			byWeek.putIfAbsent(row.getWeekStart(), new WeekEntry(row.getId(), row.getWeekStart(), row.getCreatedAt(),
				row.getSummaryEnc(), row.getTraitsEnc(), row.getSampleEnc()));
		}
		return new PersonaInput(range, List.copyOf(diaryEntries), List.copyOf(byWeek.values()));
	}
}
