package com.dearus.persona;

import com.dearus.upload.WeeklySummarizer.DatedText;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 시점 분석에 넣을 메신저 원문을 정해진 글자 수 안에서 골라요.
 * 넘치면 날짜마다 한 줄씩 돌아가며 골라서(라운드 로빈) 말이 많은 날이 다른 날을 밀어내지 않아요.
 * 결과는 날짜 순이고, 같은 날 안에서는 원래 순서예요.
 */
final class MessengerRawPicker {

	/** 구간 하나에 넣는 원문 전체 글자 수 한도(약 1만 자, 2026.11.9 결정). */
	static final int MAX_CHARS = 10_000;
	/** 한 줄이 너무 길면 이만큼만 넣어요(긴 붙여넣기 하나가 한도를 다 쓰지 않게). */
	static final int MAX_LINE_CHARS = 300;

	private MessengerRawPicker() {
	}

	static List<DatedText> pick(List<DatedText> lines, int maxChars) {
		Map<LocalDate, Deque<Indexed>> byDate = new TreeMap<>();
		for (int i = 0; i < lines.size(); i++) {
			DatedText line = lines.get(i);
			String text = line.text().strip();
			if (text.isEmpty()) {
				continue;
			}
			if (text.length() > MAX_LINE_CHARS) {
				text = text.substring(0, MAX_LINE_CHARS) + "…";
			}
			byDate.computeIfAbsent(line.date(), k -> new ArrayDeque<>()).add(new Indexed(i, new DatedText(line.date(), text)));
		}

		List<Indexed> picked = new ArrayList<>();
		int used = 0;
		boolean full = false;
		while (!full && byDate.values().stream().anyMatch(queue -> !queue.isEmpty())) {
			for (Deque<Indexed> queue : byDate.values()) {
				Indexed next = queue.peekFirst();
				if (next == null) {
					continue;
				}
				int size = next.line().text().length();
				if (used + size > maxChars) {
					full = true;
					break;
				}
				queue.pollFirst();
				picked.add(next);
				used += size;
			}
		}
		picked.sort(Comparator.comparing((Indexed item) -> item.line().date()).thenComparingInt(Indexed::index));
		return picked.stream().map(Indexed::line).toList();
	}

	private record Indexed(int index, DatedText line) {
	}
}
