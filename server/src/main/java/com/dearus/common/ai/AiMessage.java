package com.dearus.common.ai;

/** AI에 보내는 메시지 한 개예요. 글자만 보내요(메신저가 txt만 받기로 해서 이미지 입력은 쓰지 않아요, 2026.11.11). */
public record AiMessage(Role role, String text) {

	public enum Role {
		USER, ASSISTANT
	}
}
