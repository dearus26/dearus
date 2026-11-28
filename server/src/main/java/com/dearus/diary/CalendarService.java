package com.dearus.diary;

import com.dearus.common.ApiException;
import com.dearus.common.CryptoService;
import com.dearus.persona.MessengerDays;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 캘린더(GET /calendar, 2026.11.28)예요. 일기를 쓴 날과 메신저로 내 말을 올린 날을 함께 돌려줘요.
 * 메신저 날은 시점 분석과 같은 규칙(MessengerDays: 날짜마다 그날을 담은 최근 업로드, 그날 말이 실제로 있어야 함)으로 세고,
 * 업로드할 때 AI가 만든 추천 감정 · 한 줄 요약을 함께 줘요. DB만 읽어요(AI를 부르지 않아요).
 * 일기 본문은 넣지 않아요(그날을 누르면 GET /diaries의 내용을 보여줘요).
 */
@Service
public class CalendarService {

	/** 한 번에 볼 수 있는 기간(양 끝 포함, GET /diaries와 같아요) */
	static final int MAX_RANGE_DAYS = 93;

	private final DiaryRepository diaries;
	private final MessengerDays messengerDays;
	private final CryptoService crypto;

	public CalendarService(DiaryRepository diaries, MessengerDays messengerDays, CryptoService crypto) {
		this.diaries = diaries;
		this.messengerDays = messengerDays;
		this.crypto = crypto;
	}

	@Transactional(readOnly = true)
	public CalendarResponse days(UUID userId, LocalDate from, LocalDate to) {
		if (from.isAfter(to)) {
			throw ApiException.validation("from", "시작 날짜가 끝 날짜보다 늦을 수 없어요");
		}
		if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
			throw ApiException.validation("to", "한 번에 " + MAX_RANGE_DAYS + "일까지만 볼 수 있어요");
		}
		Map<LocalDate, Diary> written = diaries.findByUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(userId, from, to).stream()
			.collect(Collectors.toMap(Diary::getDiaryDate, Function.identity(), (a, b) -> a));
		Map<LocalDate, MessengerDays.Day> uploaded = messengerDays.days(userId, from, to);

		Set<LocalDate> dates = new TreeSet<>(written.keySet());
		dates.addAll(uploaded.keySet());
		List<CalendarDayResponse> items = new ArrayList<>();
		for (LocalDate date : dates) {
			Diary diary = written.get(date);
			MessengerDays.Day messenger = uploaded.get(date);
			items.add(new CalendarDayResponse(
				date,
				diary != null,
				diary == null ? List.of() : diary.getEmotions().stream().sorted().toList(),
				messenger != null,
				messenger == null ? List.of() : messenger.suggestedEmotions(),
				messenger == null || messenger.lineEnc() == null ? null : crypto.decrypt(messenger.lineEnc())));
		}
		return new CalendarResponse(items);
	}
}
