package com.dearus.persona;

import com.dearus.common.CryptoService;
import com.dearus.common.Kst;
import com.dearus.common.Weeks;
import com.dearus.diary.Diary;
import com.dearus.diary.DiaryRepository;
import com.dearus.persona.PersonaInput.DiaryEntry;
import com.dearus.persona.PersonaInput.OriginalEntry;
import com.dearus.persona.PersonaInput.WeekEntry;
import com.dearus.upload.MessengerDayNote;
import com.dearus.upload.MessengerDayNoteRepository;
import com.dearus.upload.MessengerOriginal;
import com.dearus.upload.MessengerStatus;
import com.dearus.upload.MessengerUpload;
import com.dearus.upload.MessengerUploadRepository;
import com.dearus.upload.MessengerWeeklySummary;
import com.dearus.upload.MessengerWeeklySummaryRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고른 구간의 일기, 메신저 주간 요약, 그 요약을 만든 업로드의 내 말 원문을 DB에서 모아요(AI를 부르지 않아요).
 *
 * 메신저는 날짜마다 **그날을 담은(올린 기간에 그날이 들어 있는) 가장 최근 업로드**가 그날의 주인이에요(2026.11.28).
 * 앱이 하루치씩 올려서 같은 주에도 업로드가 여럿 생기는데, 예전처럼 "한 주에 최근 업로드 하나"만 쓰면 앞서 올린 날이 사라져서 바꿨어요.
 * 주간 요약은 그 주에 주인인 날이 하나라도 있는 업로드의 것을 모두 쓰고, 원문은 업로드마다 주인인 날의 말만 써요.
 * 같은 날을 다시 올리면 새 업로드가 주인이 되어 예전 것은 그날에 쓰이지 않아요. 요약을 합치지 않아서 업로드를 지우면 그 업로드의 내용만 빠져요.
 * 하루 단위(DAY)는 그날의 주인 업로드 원문을 풀어서 그날 실제로 쓴 내 말이 있는지 확인해요(기록 수준이 이걸로 정해져요).
 */
@Component
public class PersonaCollector {

	private final DiaryRepository diaries;
	private final MessengerWeeklySummaryRepository summaries;
	private final MessengerUploadRepository uploads;
	private final MessengerDayNoteRepository dayNotes;
	private final CryptoService crypto;

	public PersonaCollector(DiaryRepository diaries, MessengerWeeklySummaryRepository summaries, MessengerUploadRepository uploads,
		MessengerDayNoteRepository dayNotes, CryptoService crypto) {
		this.diaries = diaries;
		this.summaries = summaries;
		this.uploads = uploads;
		this.dayNotes = dayNotes;
		this.crypto = crypto;
	}

	@Transactional(readOnly = true)
	public PersonaInput collect(UUID userId, PersonaRange range) {
		List<DiaryEntry> diaryEntries = new ArrayList<>();
		for (Diary diary : diaries.findByUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(userId, range.start(), range.end())) {
			diaryEntries.add(new DiaryEntry(diary.getId(), diary.getDiaryDate(), diary.getUpdatedAt(),
				diary.getEmotions().stream().sorted().toList(), diary.getContent()));
		}

		OffsetDateTime now = Kst.now();
		// 요약을 쓰는 주 전체(구간과 겹치는 주의 월요일 ~ 일요일)에서 날짜의 주인을 정해요
		LocalDate spanFrom = Weeks.startOf(range.start());
		LocalDate spanTo = Weeks.startOf(range.end()).plusDays(6);
		List<MessengerUpload> covering = uploads.findCovering(userId, spanFrom, spanTo, now, MessengerStatus.DONE);
		Map<UUID, MessengerUpload> uploadById = new LinkedHashMap<>();
		Map<LocalDate, UUID> ownerByDate = new TreeMap<>();
		for (MessengerUpload upload : covering) { // 최근 업로드부터라 먼저 차지한 업로드가 그날의 주인이에요
			uploadById.put(upload.getId(), upload);
			LocalDate from = later(upload.getPeriodStart(), spanFrom);
			LocalDate to = earlier(upload.getPeriodEnd(), spanTo);
			for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
				ownerByDate.putIfAbsent(date, upload.getId());
			}
		}

		// 하루 단위는 그날의 주인 업로드에 그날 쓴 말이 실제로 있는지 봐요. 없으면(그 기간에 그날만 대화가 없었음) 그날은 메신저 기록이 없어요
		Map<UUID, byte[]> originalById = new LinkedHashMap<>();
		if (range.scope() == PersonaScope.DAY) {
			UUID owner = ownerByDate.get(range.anchor());
			if (owner != null && !hasLinesOn(owner, range.anchor(), originalById)) {
				ownerByDate.remove(range.anchor());
			}
		}

		Map<UUID, Set<LocalDate>> datesByUpload = new LinkedHashMap<>();
		ownerByDate.forEach((date, uploadId) -> datesByUpload.computeIfAbsent(uploadId, k -> new TreeSet<>()).add(date));

		List<WeekEntry> weekEntries = new ArrayList<>();
		for (MessengerWeeklySummary row : summaries.findUsable(userId, range.weekFrom(), range.weekTo(), now)) {
			LocalDate weekStart = row.getWeekStart();
			LocalDate weekEnd = weekStart.plusDays(6);
			MessengerUpload upload = uploadById.get(row.getUploadId());
			Set<LocalDate> owned = datesByUpload.getOrDefault(row.getUploadId(), Set.of());
			if (upload == null || owned.stream().noneMatch(date -> !date.isBefore(weekStart) && !date.isAfter(weekEnd))) {
				continue; // 이 주에서 이 업로드가 주인인 날이 없어요(더 최근 업로드가 같은 날을 다시 올렸어요)
			}
			weekEntries.add(new WeekEntry(row.getId(), weekStart, later(upload.getPeriodStart(), weekStart),
				earlier(upload.getPeriodEnd(), weekEnd), row.getCreatedAt(), row.getSummaryEnc(), row.getTraitsEnc(), row.getSampleEnc()));
		}
		if (range.scope() == PersonaScope.DAY) {
			weekEntries = dayWeeks(range, weekEntries, ownerByDate.get(range.anchor()), !diaryEntries.isEmpty());
		}

		// 원문은 이 구간 안에서 주인인 날이 있는 업로드에서만 가져와요. V7 이전 업로드처럼 원문이 없으면 건너뛰어요.
		List<OriginalEntry> originals = new ArrayList<>();
		int messengerDays = 0;
		for (Map.Entry<UUID, Set<LocalDate>> entry : datesByUpload.entrySet()) {
			Set<LocalDate> inRange = new TreeSet<>();
			for (LocalDate date : entry.getValue()) {
				if (!date.isBefore(range.start()) && !date.isAfter(range.end())) {
					inRange.add(date);
				}
			}
			if (inRange.isEmpty()) {
				continue;
			}
			messengerDays += inRange.size();
			byte[] originalEnc = originalById.containsKey(entry.getKey()) ? originalById.get(entry.getKey()) : uploads.findOriginal(entry.getKey());
			if (originalEnc != null) {
				originals.add(new OriginalEntry(entry.getKey(), Set.copyOf(inRange), originalEnc));
			}
		}
		// 날마다의 한 줄 요약도 그날 주인 업로드 것만, 구간 안의 날만 가져와요(2026.11.28)
		List<PersonaInput.DayLine> dayLines = new ArrayList<>();
		Set<UUID> ownersInRange = new HashSet<>();
		for (OriginalEntry original : originals) {
			ownersInRange.add(original.uploadId());
		}
		if (!ownersInRange.isEmpty()) {
			for (MessengerDayNote note : dayNotes.findByUploadIdInAndDayBetween(ownersInRange, range.start(), range.end())) {
				if (note.getLineEnc() != null && note.getUploadId().equals(ownerByDate.get(note.getDay()))) {
					dayLines.add(new PersonaInput.DayLine(note.getDay(), note.getLineEnc()));
				}
			}
			dayLines.sort(Comparator.comparing(PersonaInput.DayLine::date));
		}
		return new PersonaInput(range, List.copyOf(diaryEntries), List.copyOf(weekEntries), List.copyOf(originals), messengerDays,
			dayLines);
	}

	/**
	 * 하루 단위에 붙이는 말투 분석(traits)은 하나예요. 그날을 올린 업로드의 요약이 있으면 그것이고,
	 * 그날 메신저는 없지만 일기가 있으면 그 주의 가장 최근 요약의 말투를 더해요(2026.10.28 결정). 둘 다 아니면 없어요.
	 */
	private static List<WeekEntry> dayWeeks(PersonaRange range, List<WeekEntry> candidates, UUID dayOwner, boolean hasDiary) {
		if (dayOwner != null) {
			for (WeekEntry week : candidates) {
				if (!range.anchor().isBefore(week.from()) && !range.anchor().isAfter(week.to())) {
					return List.of(week);
				}
			}
		}
		if (hasDiary && !candidates.isEmpty()) {
			return List.of(candidates.get(0)); // 같은 주 안에서는 최근 요약이 먼저예요
		}
		return List.of();
	}

	private boolean hasLinesOn(UUID uploadId, LocalDate date, Map<UUID, byte[]> originalById) {
		byte[] originalEnc = uploads.findOriginal(uploadId);
		originalById.put(uploadId, originalEnc);
		if (originalEnc == null) {
			return false; // 원문이 없는 예전 업로드는 그날 말이 있는지 알 수 없어서 기록으로 치지 않아요
		}
		return MessengerOriginal.fromJson(crypto.decrypt(originalEnc)).stream()
			.anyMatch(line -> line.date().equals(date) && !line.text().isBlank());
	}

	private static LocalDate later(LocalDate a, LocalDate b) {
		return a.isAfter(b) ? a : b;
	}

	private static LocalDate earlier(LocalDate a, LocalDate b) {
		return a.isBefore(b) ? a : b;
	}
}
