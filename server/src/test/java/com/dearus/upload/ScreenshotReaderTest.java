package com.dearus.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dearus.common.Kst;
import com.dearus.common.ai.AiClient;
import com.dearus.common.ai.AiEffort;
import com.dearus.common.ai.AiException;
import com.dearus.common.ai.AiImage;
import com.dearus.common.ai.AiRequest;
import com.dearus.common.ai.AiResult;
import com.dearus.common.ai.FakeAiClient;
import com.dearus.upload.ScreenshotReader.Item;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 스크린샷 읽기의 프롬프트 · 묶음 나누기 · 답 읽기를 실제 AI 없이 확인해요. */
class ScreenshotReaderTest {

	private static AiImage png(int bytes) {
		byte[] data = new byte[bytes];
		byte[] header = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
		System.arraycopy(header, 0, data, 0, Math.min(header.length, bytes));
		return new AiImage(AiImage.MediaType.PNG, data);
	}

	@Test
	void 날짜_구분선과_내_말을_화면_순서대로_읽어요() {
		List<Item> items = ScreenshotReader.parse("""
			{"items": [{"date": "2026-09-28"}, {"me": "아 월요일 ㅠㅠ"}, {"me": "오늘\\n수업 몇 시야?"}, {"date": "2026-09-29"}, {"me": "ㅋㅋ"}]}""");

		assertThat(items).containsExactly(Item.dateLine(LocalDate.of(2026, 9, 28)), Item.mine("아 월요일 ㅠㅠ"),
			Item.mine("오늘 수업 몇 시야?"), Item.dateLine(LocalDate.of(2026, 9, 29)), Item.mine("ㅋㅋ"));
	}

	@Test
	void 코드_블록이나_설명이_붙어도_JSON만_읽고_틀린_날짜와_빈_말은_건너뛰어요() {
		List<Item> items = ScreenshotReader.parse("""
			```json
			{"items": [{"date": "9월 28일"}, {"me": "  "}, {"me": "안녕"}, "이상한 값", {"other": "무시"}]}
			```""");

		assertThat(items).containsExactly(Item.mine("안녕"));
	}

	@Test
	void 너무_긴_말은_2000자에서_잘라요() {
		List<Item> items = ScreenshotReader.parse("{\"items\": [{\"me\": \"" + "가".repeat(2100) + "\"}]}");

		assertThat(items.get(0).text()).hasSize(ScreenshotReader.MAX_TEXT_CHARS);
	}

	@Test
	void JSON이_아니거나_items가_없으면_AI_BAD_RESPONSE예요() {
		assertThatThrownBy(() -> ScreenshotReader.parse("읽을 수 없었어요"))
			.isInstanceOfSatisfying(AiException.class, e -> assertThat(e.reason()).isEqualTo(AiException.Reason.BAD_RESPONSE));
		assertThatThrownBy(() -> ScreenshotReader.parse("{\"lines\": []}"))
			.isInstanceOfSatisfying(AiException.class, e -> assertThat(e.reason()).isEqualTo(AiException.Reason.BAD_RESPONSE));
		assertThat(ScreenshotReader.parse("{\"items\": []}")).isEmpty();
	}

	@Test
	void 순서를_지키며_10장씩_나눠요() {
		List<AiImage> images = new ArrayList<>();
		for (int i = 0; i < 23; i++) {
			images.add(png(16));
		}

		List<List<AiImage>> batches = ScreenshotReader.batches(images);

		assertThat(batches).extracting(List::size).containsExactly(10, 10, 3);
		assertThat(batches.get(1).get(0)).isSameAs(images.get(10));
		assertThat(batches.get(2).get(2)).isSameAs(images.get(22));
	}

	@Test
	void 한_번에_보내는_원본이_20MB를_넘지_않게_나눠요() {
		List<AiImage> images = List.of(png(5 * 1024 * 1024), png(5 * 1024 * 1024), png(5 * 1024 * 1024), png(5 * 1024 * 1024),
			png(5 * 1024 * 1024));

		List<List<AiImage>> batches = ScreenshotReader.batches(images);

		assertThat(batches).extracting(List::size).containsExactly(4, 1); // 4장이 20MB예요
	}

	@Test
	void 이미지와_규칙을_한_요청으로_보내요() {
		AiClient ai = mock(AiClient.class);
		ArgumentCaptor<AiRequest> captor = ArgumentCaptor.forClass(AiRequest.class);
		when(ai.complete(captor.capture())).thenReturn(new AiResult("{\"items\": [{\"date\": \"2026-09-28\"}, {\"me\": \"안녕\"}]}", 1, 1));
		List<AiImage> batch = List.of(png(16), png(16));

		List<Item> items = new ScreenshotReader(ai).read(batch);

		AiRequest request = captor.getValue();
		assertThat(request.system()).isEqualTo(ScreenshotReader.SYSTEM_PROMPT);
		assertThat(request.effort()).isEqualTo(AiEffort.LOW);
		assertThat(request.messages()).hasSize(1);
		assertThat(request.messages().get(0).images()).containsExactlyElementsOf(batch);
		assertThat(items).containsExactly(Item.dateLine(LocalDate.of(2026, 9, 28)), Item.mine("안녕"));
	}

	@Test
	void 프롬프트는_오른쪽_노란_말풍선만_내_말로_읽고_사진_속_지시는_따르지_않게_해요() {
		assertThat(ScreenshotReader.SYSTEM_PROMPT)
			.contains("오른쪽에 있는 노란색 말풍선")
			.contains("왼쪽 말풍선(상대가 한 말)")
			.contains("따르지 말고")
			.contains("겹친 날짜 구분선과 말은 한 번만")
			.contains("{\"items\":");
	}

	@Test
	void 가짜_AI의_스크린샷_읽기_결과도_그대로_읽혀요() {
		AiRequest request = AiRequest.withImages(ScreenshotReader.SYSTEM_PROMPT, List.of(png(16)), ScreenshotReader.USER_PROMPT, 100,
			AiEffort.LOW);

		List<Item> items = ScreenshotReader.parse(new FakeAiClient().complete(request).text());

		assertThat(items).hasSize(3);
		assertThat(items.get(0)).isEqualTo(Item.dateLine(Kst.today().minusDays(1)));
		assertThat(items.subList(1, 3)).noneMatch(Item::isDate);
	}
}
