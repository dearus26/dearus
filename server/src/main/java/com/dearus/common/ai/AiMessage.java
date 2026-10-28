package com.dearus.common.ai;

public record AiMessage(Role role, String text) {

	public enum Role {
		USER, ASSISTANT
	}
}
