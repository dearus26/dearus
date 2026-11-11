package com.dearus.upload;

import static org.assertj.core.api.Assertions.assertThat;

import com.dearus.common.Kst;
import com.dearus.common.ai.AiImage;
import com.dearus.common.ai.AiProperties;
import com.dearus.common.ai.AnthropicAiClient;
import com.dearus.upload.ScreenshotReader.Item;
import com.dearus.upload.WeeklySummarizer.DatedText;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 진짜 Claude Haiku 5.5로 카카오톡 스크린샷을 읽어 보는 시험이에요("내 말 구분" 목표: 스크린샷 12장 기준 90% 이상).
 * 비용이 들고 사진이 필요해서 ANTHROPIC_API_KEY와 DEARUS_SCREENSHOT_DIR(스크린샷 폴더)가 모두 있을 때만 돌아요.
 * 폴더 안의 JPEG · PNG · WebP를 파일 이름 순서(= 대화 순서)로 읽고, 읽어낸 날짜와 내 말을 출력해요. 정확도는 출력을 보고 사람이 세요.
 * 돌리기: ANTHROPIC_API_KEY=... DEARUS_SCREENSHOT_DIR=... ./gradlew test --tests '*ScreenshotReaderLiveTest' -i
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DEARUS_SCREENSHOT_DIR", matches = ".+")
class ScreenshotReaderLiveTest {

	private final ScreenshotReader reader = new ScreenshotReader(
		new AnthropicAiClient(new AiProperties("anthropic", System.getenv("ANTHROPIC_API_KEY"), "claude-haiku-5-5", 90)));

	@Test
	void 폴더의_스크린샷에서_날짜와_내_말을_읽어요() throws IOException {
		List<AiImage> images = new ArrayList<>();
		try (Stream<Path> files = Files.list(Path.of(System.getenv("DEARUS_SCREENSHOT_DIR")))) {
			for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
				byte[] data = Files.readAllBytes(file);
				AiImage.MediaType.detect(data).ifPresent(type -> images.add(new AiImage(type, data)));
			}
		}
		assertThat(images).as("스크린샷 폴더에 JPEG · PNG · WebP가 있어야 해요").isNotEmpty();

		long started = System.nanoTime();
		List<List<Item>> batches = new ArrayList<>();
		for (List<AiImage> batch : ScreenshotReader.batches(images)) {
			batches.add(reader.read(batch));
		}
		List<Item> items = MessengerImageJob.stitch(batches);
		MessengerImageJob.Dated dated = MessengerImageJob.assignDates(items, Kst.today());

		System.out.println("[live] 스크린샷 " + images.size() + "장, 묶음 " + batches.size() + "개, " + (System.nanoTime() - started) / 1_000_000
			+ "ms, 읽은 항목 " + items.size() + "개, 날짜를 붙인 내 말 " + dated.lines().size() + "개, 실패 코드 " + dated.failureCode());
		for (DatedText line : dated.lines()) {
			System.out.println("[live] " + line.date() + " | " + line.text());
		}
		assertThat(dated.failureCode()).isNull();
		assertThat(dated.lines()).isNotEmpty();
	}
}
