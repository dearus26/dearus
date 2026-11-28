package com.dearus.persona;

/**
 * 고른 구간에 기록이 얼마나 있는지. 기준은 API 명세의 RecordLevel 표이고, 팀이 바꾸면 이 메서드만 바꿔요.
 * AROUND: 일기 10건 이상 RICH, 5건 이상이거나 메신저 1주 이상 ENOUGH, 1건 이상 LOW, 없으면 NONE.
 * DAY: 그날의 일기가 있으면 ENOUGH. 일기가 없어도 그날 메신저로 올린 내 말이 있으면 LOW(말투와 그날 한 말은 알지만 일기보다 적어요).
 * 둘 다 없으면 NONE이에요. 같은 주의 다른 날만 올렸으면 그날은 NONE이에요(2026.11.28, 올리지 않은 날은 기록 없음).
 */
public enum RecordLevel {
	NONE, LOW, ENOUGH, RICH;

	/** messengerDays는 DAY에서만 봐요(그날 올린 내 말이 있으면 1). AROUND는 messengerWeeks로 봐요. */
	public static RecordLevel of(PersonaScope scope, int diaryCount, int messengerWeeks, int messengerDays) {
		if (scope == PersonaScope.DAY) {
			if (diaryCount >= 1) {
				return ENOUGH;
			}
			return messengerDays >= 1 ? LOW : NONE;
		}
		if (diaryCount >= 10) {
			return RICH;
		}
		if (diaryCount >= 5 || messengerWeeks >= 1) {
			return ENOUGH;
		}
		return diaryCount >= 1 ? LOW : NONE;
	}
}
