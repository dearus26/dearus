package com.dearus.diary;

import java.util.List;

/** API 명세의 CalendarDayList. 기록(일기 또는 메신저)이 있는 날만 날짜 오름차순이에요. */
public record CalendarResponse(List<CalendarDayResponse> items) {
}
