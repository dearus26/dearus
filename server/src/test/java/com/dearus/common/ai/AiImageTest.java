package com.dearus.common.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 이미지 형식 알아내기 · 이미지 메시지 규칙 · Anthropic 요청 블록 순서를 AI 호출 없이 확인해요. */
class AiImageTest {

	static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
	static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2};
	static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 1};

	@Test
	void 파일_앞부분으로_JPEG_PNG_WebP를_알아내요() {
		assertThat(AiImage.MediaType.detect(PNG)).contains(AiImage.MediaType.PNG);
		assertThat(AiImage.MediaType.detect(JPEG)).contains(AiImage.MediaType.JPEG);
		assertThat(AiImage.MediaType.detect(WEBP)).contains(AiImage.MediaType.WEBP);
	}

	@Test
	void 그_밖의_파일이나_너무_짧은_파일은_알아내지_못해요() {
		assertThat(AiImage.MediaType.detect("GIF89a...".getBytes())).isEmpty(); // GIF는 받지 않아요
		assertThat(AiImage.MediaType.detect("그냥 글자예요".getBytes())).isEmpty();
		assertThat(AiImage.MediaType.detect(new byte[] {(byte) 0x89, 0x50})).isEmpty();
		assertThat(AiImage.MediaType.detect(new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'})).isEmpty();
		assertThat(AiImage.MediaType.detect(null)).isEmpty();
	}

	@Test
	void 빈_이미지는_만들_수_없고_toString에는_바이트가_찍히지_않아요() {
		assertThatThrownBy(() -> new AiImage(AiImage.MediaType.PNG, new byte[0])).isInstanceOf(IllegalArgumentException.class);
		assertThat(new AiImage(AiImage.MediaType.PNG, PNG).toString()).isEqualTo("AiImage[PNG, 11 bytes]");
	}

	@Test
	void 이미지는_사용자_메시지에만_붙이고_이미지_요청은_한_장_이상이어야_해요() {
		AiImage image = new AiImage(AiImage.MediaType.PNG, PNG);
		assertThatThrownBy(() -> new AiMessage(AiMessage.Role.ASSISTANT, "답", List.of(image))).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> AiRequest.withImages("system", List.of(), "읽어 주세요", 100, AiEffort.LOW))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(new AiMessage(AiMessage.Role.USER, "글자만").images()).isEmpty();
	}

	@Test
	void Anthropic_요청에는_이미지를_순서대로_먼저_놓고_글자를_마지막에_놓아요() {
		AiMessage message = new AiMessage(AiMessage.Role.USER, "옮겨 적어 주세요",
			List.of(new AiImage(AiImage.MediaType.PNG, PNG), new AiImage(AiImage.MediaType.JPEG, JPEG), new AiImage(AiImage.MediaType.WEBP, WEBP)));

		List<ContentBlockParam> blocks = AnthropicAiClient.blocks(message);

		assertThat(blocks).hasSize(4);
		assertThat(blocks.subList(0, 3)).allMatch(ContentBlockParam::isImage);
		Base64ImageSource first = blocks.get(0).asImage().source().asBase64();
		assertThat(first.mediaType()).isEqualTo(Base64ImageSource.MediaType.IMAGE_PNG);
		assertThat(Base64.getDecoder().decode(first.data())).isEqualTo(PNG);
		assertThat(blocks.get(1).asImage().source().asBase64().mediaType()).isEqualTo(Base64ImageSource.MediaType.IMAGE_JPEG);
		assertThat(blocks.get(2).asImage().source().asBase64().mediaType()).isEqualTo(Base64ImageSource.MediaType.IMAGE_WEBP);
		assertThat(blocks.get(3).isText()).isTrue();
		assertThat(blocks.get(3).asText().text()).isEqualTo("옮겨 적어 주세요");
	}
}
