package com.dearus.chat;

import com.dearus.diary.Emotion;
import com.dearus.persona.PersonaScope;
import com.dearus.persona.RecordLevel;
import com.dearus.persona.ShortcutKey;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * API 명세의 ChatListItem. "10월 4일의 나", "3개월 전의 나" 같은 제목은 앱이 scope · anchorDate로 만들어요.
 * emotions는 DAY일 때만 채워지고(그날 일기의 감정), lastMessageAt은 대화가 없으면 세션을 만든 시각이에요.
 */
public record ChatListItemResponse(
	UUID id,
	PersonaScope scope,
	ShortcutKey shortcutKey,
	LocalDate anchorDate,
	List<Emotion> emotions,
	RecordLevel level,
	ChatStatus status,
	String lastMessagePreview,
	OffsetDateTime lastMessageAt,
	OffsetDateTime startedAt,
	OffsetDateTime endedAt) {
}
