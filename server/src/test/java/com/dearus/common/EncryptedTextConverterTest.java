package com.dearus.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** 일기 본문처럼 엔티티 필드를 암호화해 저장하는 변환기를 확인해요. */
class EncryptedTextConverterTest {

	private static final byte[] RAW_KEY = new byte[32];

	static {
		Arrays.fill(RAW_KEY, (byte) 3);
	}

	private final CryptoService crypto = new CryptoService(new CryptoProperties(Base64.getEncoder().encodeToString(RAW_KEY)));
	private final EncryptedTextConverter converter = new EncryptedTextConverter(crypto);

	@Test
	void 저장할_때_잠그고_읽을_때_그대로_풀어요() {
		String diary = "오늘은 회사에서 많이 울었어요. 내일은 괜찮겠지 😢";

		byte[] stored = converter.convertToDatabaseColumn(diary);

		assertThat(new String(stored, StandardCharsets.UTF_8)).doesNotContain("회사");
		assertThat(converter.convertToEntityAttribute(stored)).isEqualTo(diary);
		assertThat(crypto.decrypt(stored)).isEqualTo(diary); // 다른 *_enc 컬럼과 같은 형식이에요
	}

	@Test
	void null은_null로_둬요() {
		assertThat(converter.convertToDatabaseColumn(null)).isNull();
		assertThat(converter.convertToEntityAttribute(null)).isNull();
	}
}
