package com.dearus.persona;

/**
 * 고른 구간에 기록이 얼마나 있는지. 기준은 API 명세의 임시 초안이고, 팀이 정하면 이 메서드만 바꿔요.
 * AROUND: 일기 10건 이상 RICH, 5건 이상이거나 메신저 1주 이상 ENOUGH, 1건 이상 LOW, 없으면 NONE.
 * DAY: 그날의 일기만 봐요(메신저 말투 분석을 더해도 기준에 넣지 않아요). 일기가 있으면 ENOUGH, 없으면 NONE.
 */
public enum RecordLevel {
	NONE, LOW, ENOUGH, RICH;

	public static RecordLevel of(PersonaScope scope, int diaryCount, int messengerWeeks) {
		if (scope == PersonaScope.DAY) {
			return diaryCount >= 1 ? ENOUGH : NONE;
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
