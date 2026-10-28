package com.dearus.chat;

import java.util.List;

public record MessageListResponse(List<ChatMessageResponse> items) {
}
