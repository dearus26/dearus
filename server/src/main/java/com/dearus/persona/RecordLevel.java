package com.dearus.persona;

/**
 * 고른 구간에 기록이 얼마나 있는지. 기준은 API 명세의 임시 초안이고, 팀이 정하면 이 메서드만 바꿔요.
 * AROUND: 일기 10건 이상 RICH, 5건 이상이거나 메신저 1주 이상 ENOUGH, 1건 이상 LOW, 없으면 NONE.
 * DAY: 그날의 일기가 있으면 ENOUGH. 일기가 없어도 그날이 속한 주의 메신저 말투 분석이 있으면 LOW(말투는 알지만 그날의 일은 잘 몰라요).
 * 둘 다 없으면 NONE이에요. 일기를 쓰기 귀찮을 때 카카오톡 txt로 대신하는 것이 기본 흐름이라, 메신저만 올린 날도 기록이 있는 날로 봐요(2026.11.25).
 */
public enum RecordLevel {
	NONE, LOW, ENOUGH, RICH;

	public static RecordLevel of(PersonaScope scope, int diaryCount, int messengerWeeks) {
		if (scope == PersonaScope.DAY) {
			if (diaryCount >= 1) {
				return ENOUGH;
			}
			return messengerWeeks >= 1 ? LOW : NONE;
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
