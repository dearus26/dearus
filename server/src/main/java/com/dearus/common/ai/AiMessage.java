package com.dearus.common.ai;

import java.util.List;

/** AI에 보내는 메시지 한 개예요. images는 사용자 메시지에만 붙일 수 있고, 글자보다 앞에 놓여 보내져요. */
public record AiMessage(Role role, String text, List<AiImage> images) {

	public enum Role {
		USER, ASSISTANT
	}

	public AiMessage {
		images = images == null ? List.of() : List.copyOf(images);
		if (role == Role.ASSISTANT && !images.isEmpty()) {
			throw new IllegalArgumentException("이미지는 사용자 메시지에만 붙여요");
		}
	}

	/** 글자만 있는 메시지예요. */
	public AiMessage(Role role, String text) {
		this(role, text, List.of());
	}
}
