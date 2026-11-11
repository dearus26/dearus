package com.dearus.couple;

import java.util.List;

/** API 명세의 CoupleDayList (날짜 오름차순) */
public record CoupleDayListResponse(List<CoupleDayResponse> items) {
}
