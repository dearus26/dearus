package com.dearus.chat;

import java.time.LocalDate;

/** API 명세의 Usage. 오늘(한국 시간) 대화한 시간(분)과 권장 시간이에요. */
public record UsageResponse(LocalDate date, int chatMinutes, int recommendedMinutes) {
}
