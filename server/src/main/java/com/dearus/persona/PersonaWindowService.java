package com.dearus.persona;

import com.dearus.common.Kst;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 시점 고르기 화면용이에요. 고른 구간에 기록이 얼마나 있는지만 알려주고 AI는 부르지 않아요. */
@Service
public class PersonaWindowService {

	private final PersonaCollector collector;

	public PersonaWindowService(PersonaCollector collector) {
		this.collector = collector;
	}

	/** GET /personas/preview — 고른 날짜(+ 범위)의 기록량 */
	public PersonaWindowResponse window(UUID userId, PersonaScope scope, LocalDate anchor) {
		PersonaRange range = PersonaRange.of(scope, anchor, Kst.today());
		return PersonaWindowResponse.from(collector.collect(userId, range));
	}

	/** GET /personas/shortcuts — 1개월 · 6개월 · 1년 전 바로가기 세 개 (각각 전후 2주) */
	public ShortcutListResponse shortcuts(UUID userId) {
		LocalDate today = Kst.today();
		List<ShortcutResponse> items = new ArrayList<>();
		for (ShortcutKey key : ShortcutKey.values()) {
			PersonaRange range = PersonaRange.of(PersonaScope.AROUND, PersonaRange.shortcutAnchor(key, today), today);
			items.add(ShortcutResponse.of(key, PersonaWindowResponse.from(collector.collect(userId, range))));
		}
		return new ShortcutListResponse(items);
	}
}
