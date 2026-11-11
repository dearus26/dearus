package com.dearus.couple;

import com.dearus.common.ApiException;
import com.dearus.common.Kst;
import com.dearus.diary.Diary;
import com.dearus.diary.DiaryRepository;
import com.dearus.diary.Emotion;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 함께 볼 날 고르기(GET /couples/days)예요. 두 사람의 일기가 모두 있는 날만 돌려주고, 감정이 엇갈린 날을 추천해요.
 * 대화를 시작할 수 있는 날(오늘부터 1년 전까지)만 돌려줘요. DB만 읽어서 빨라요(AI를 부르지 않아요).
 */
@Service
public class CoupleDayService {

	/** 한 번에 볼 수 있는 기간(양 끝 포함, API 명세: 최대 93일) */
	static final int MAX_DAYS = 93;
	/** 시점은 오늘부터 이만큼 전까지만 고를 수 있어요(PersonaRange와 같아요) */
	static final int MAX_DAYS_BACK = 365;
	/** 추천에 쓰는 힘든 감정이에요 */
	static final Set<Emotion> HARD = EnumSet.of(Emotion.ANGRY, Emotion.SAD, Emotion.ANXIOUS);

	private final CoupleAccess access;
	private final DiaryRepository diaries;

	public CoupleDayService(CoupleAccess access, DiaryRepository diaries) {
		this.access = access;
		this.diaries = diaries;
	}

	@Transactional(readOnly = true)
	public CoupleDayListResponse days(UUID userId, LocalDate from, LocalDate to) {
		if (from == null || to == null) {
			throw ApiException.validation(from == null ? "from" : "to", "꼭 필요해요");
		}
		if (to.isBefore(from)) {
			throw ApiException.validation("to", "from보다 앞설 수 없어요");
		}
		if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
			throw ApiException.validation("to", "한 번에 " + MAX_DAYS + "일까지 볼 수 있어요");
		}
		CoupleAccess.Link link = access.requireOpen(userId);
		LocalDate today = Kst.today();
		LocalDate start = from.isBefore(today.minusDays(MAX_DAYS_BACK)) ? today.minusDays(MAX_DAYS_BACK) : from;
		LocalDate end = to.isAfter(today) ? today : to;
		if (end.isBefore(start)) {
			return new CoupleDayListResponse(List.of());
		}
		Map<LocalDate, Diary> mine = byDate(diaries.findByUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(userId, start, end));
		Map<LocalDate, Diary> theirs = byDate(diaries.findByUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(link.partner().getId(), start, end));
		List<CoupleDayResponse> items = new ArrayList<>();
		for (Map.Entry<LocalDate, Diary> entry : mine.entrySet()) {
			Diary partnerDiary = theirs.get(entry.getKey());
			if (partnerDiary == null) {
				continue;
			}
			List<Emotion> myEmotions = sorted(entry.getValue().getEmotions());
			List<Emotion> partnerEmotions = sorted(partnerDiary.getEmotions());
			items.add(new CoupleDayResponse(entry.getKey(), myEmotions, partnerEmotions, recommended(myEmotions, partnerEmotions)));
		}
		items.sort((a, b) -> a.date().compareTo(b.date()));
		return new CoupleDayListResponse(items);
	}

	/**
	 * 추천 규칙(2026.11.11, 간단하게 시작해요): 두 사람 중 한쪽이라도 화남 · 슬픔 · 불안이 있고,
	 * 두 사람의 감정 태그가 다르거나 둘 다 화남인 날이에요.
	 */
	static boolean recommended(Collection<Emotion> mine, Collection<Emotion> theirs) {
		boolean hard = mine.stream().anyMatch(HARD::contains) || theirs.stream().anyMatch(HARD::contains);
		boolean different = !Set.copyOf(mine).equals(Set.copyOf(theirs));
		boolean bothAngry = mine.contains(Emotion.ANGRY) && theirs.contains(Emotion.ANGRY);
		return hard && (different || bothAngry);
	}

	private static Map<LocalDate, Diary> byDate(List<Diary> list) {
		return list.stream().collect(Collectors.toMap(Diary::getDiaryDate, Function.identity(), (a, b) -> a));
	}

	private static List<Emotion> sorted(Collection<Emotion> emotions) {
		return emotions.stream().sorted().toList();
	}
}
