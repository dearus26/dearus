package com.dearus.persona;

/** 시점 고르기의 바로가기. months는 오늘에서 거슬러 올라갈 개월 수예요. */
public enum ShortcutKey {
	ONE_MONTH(1), SIX_MONTHS(6), ONE_YEAR(12);

	private final int months;

	ShortcutKey(int months) {
		this.months = months;
	}

	public int months() {
		return months;
	}
}
