package com.dearus.persona;

import static org.assertj.core.api.Assertions.assertThat;

import com.dearus.upload.WeeklySummarizer.DatedText;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 메신저 원문을 글자 수 안에서 고르는 규칙을 확인해요. */
class MessengerRawPickerTest {

	private static final LocalDate MON = LocalDate.of(2026, 9, 28);

	private static DatedText line(int dayOffset, String text) {
		return new DatedText(MON.plusDays(dayOffset), text);
	}

	@Test
	void 한도_안이면_모두_넣고_순서를_지켜요() {
		List<DatedText> lines = List.of(line(0, "하나"), line(0, "둘"), line(1, "셋"));

		assertThat(MessengerRawPicker.pick(lines, 100)).containsExactlyElementsOf(lines);
	}

	@Test
	void 넘치면_날짜마다_돌아가며_골라서_말이_많은_날이_다른_날을_밀어내지_않아요() {
		List<DatedText> lines = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			lines.add(line(0, "월" + i)); // 월요일에 2글자짜리 10개
		}
		lines.add(line(1, "화0"));
		lines.add(line(2, "수0"));

		List<DatedText> picked = MessengerRawPicker.pick(lines, 10); // 5줄만 들어가요

		assertThat(picked).extracting(DatedText::text).containsExactly("월0", "월1", "월2", "화0", "수0");
	}

	@Test
	void 너무_긴_한_줄은_잘라서_넣고_빈_줄은_빼요() {
		List<DatedText> picked = MessengerRawPicker.pick(List.of(line(0, "가".repeat(500)), line(0, "  ")), 10_000);

		assertThat(picked).hasSize(1);
		assertThat(picked.get(0).text()).hasSize(MessengerRawPicker.MAX_LINE_CHARS + 1).endsWith("…");
	}
}
