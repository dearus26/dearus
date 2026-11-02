package com.dearus.chat;

import java.util.List;

/**
 * API 명세의 SafetyNotice. 대화에서 위기 신호를 감지했을 때의 상담전화 안내예요.
 * 번호는 설정(app.safety.hotlines)에서 읽어서 앱 배포 없이 바꿀 수 있어요.
 */
public record SafetyNotice(String reason, List<Hotline> hotlines) {

	public record Hotline(String name, String phone, String hours) {
	}

	/** 지금은 한 가지(DISTRESS)뿐이에요. */
	public static SafetyNotice distress(SafetyProperties properties) {
		return new SafetyNotice("DISTRESS", properties.hotlines());
	}
}
