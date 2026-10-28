package com.dearus.chat;

import com.dearus.persona.PersonaScope;
import com.dearus.persona.ShortcutKey;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * API 명세의 ChatCreate. 바로가기는 AROUND(전후 2주)에 shortcutKey를 같이 보내고, 캘린더에서 고른 날은 DAY예요.
 * forCouple은 커플 모드의 같은 날 비교 세션이고, 커플 기능을 만들기 전까지는 403 COUPLE_ACCESS_DENIED예요.
 */
public record ChatCreateRequest(
	@NotNull(message = "꼭 필요해요") LocalDate anchorDate,
	@NotNull(message = "꼭 필요해요") PersonaScope scope,
	ShortcutKey shortcutKey,
	Boolean forCouple) {
}
