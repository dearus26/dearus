package com.dearus.persona;

/**
 * 그때의 나를 만드는 데 쓰는 기록의 범위. AROUND는 기준일 전후 2주(바로가기), DAY는 그날 하루(캘린더에서 고른 날)예요.
 * windowDays는 ERD의 persona_analyses.window_days 값이에요.
 */
public enum PersonaScope {
	DAY(0), AROUND(14);

	private final int windowDays;

	PersonaScope(int windowDays) {
		this.windowDays = windowDays;
	}

	public int windowDays() {
		return windowDays;
	}
}
