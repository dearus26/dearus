package com.dearus.diary;

import java.time.LocalDate;
import java.util.List;

/**
 * API 명세의 CalendarDay(2026.11.28). 홈 캘린더의 하루예요.
 * emotions는 일기에 직접 고른 감정(일기가 없으면 빈 배열), suggestedEmotions는 그날 올린 메신저로 AI가 추천한 감정이에요.
 * messengerLine은 그날 메신저로 AI가 만든 한 줄 요약이에요(메신저가 없거나 2026.11.28 전 업로드면 null).
 */
public record CalendarDayResponse(
	LocalDate date,
	boolean hasDiary,
	List<Emotion> emotions,
	boolean hasMessenger,
	List<Emotion> suggestedEmotions,
	String messengerLine) {
}
