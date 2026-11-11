package com.dearus.couple;

import com.dearus.diary.Emotion;
import java.time.LocalDate;
import java.util.List;

/** API 명세의 CoupleDay. 두 사람의 일기가 모두 있는 날이에요. */
public record CoupleDayResponse(LocalDate date, List<Emotion> myEmotions, List<Emotion> partnerEmotions, boolean recommended) {
}
