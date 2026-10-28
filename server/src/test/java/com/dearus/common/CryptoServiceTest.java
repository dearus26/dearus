package com.dearus.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** 암호화해서 DB에 두는 값의 규칙(복원 · 매번 다른 바이트 · 변조 감지 · 키 검사)을 확인해요. */
class CryptoServiceTest {

	private static String keyOf(int fill) {
		byte[] raw = new byte[32];
		Arrays.fill(raw, (byte) fill);
		return Base64.getEncoder().encodeToString(raw);
	}

	private final CryptoService crypto = new CryptoService(new CryptoProperties(keyOf(7)));

	@Test
	void 암호화한_값을_그대로_복원해요() {
		String plain = "이번 주에는 시험 때문에 많이 지쳤어요. ㅋㅋ 😅";

		assertThat(crypto.decrypt(crypto.encrypt(plain))).isEqualTo(plain);
	}

	@Test
	void 같은_글자도_매번_다른_바이트가_되고_원문이_들어있지_않아요() {
		byte[] first = crypto.encrypt("같은 글자");
		byte[] second = crypto.encrypt("같은 글자");

		assertThat(first).isNotEqualTo(second);
		assertThat(new String(first, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("같은");
	}

	@Test
	void 바이트가_바뀌면_복호화에_실패해요() {
		byte[] data = crypto.encrypt("원본");
		data[data.length - 1] ^= 1;

		assertThatThrownBy(() -> crypto.decrypt(data)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 다른_키로는_풀_수_없어요() {
		byte[] data = crypto.encrypt("비밀");
		CryptoService other = new CryptoService(new CryptoProperties(keyOf(9)));

		assertThatThrownBy(() -> other.decrypt(data)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void 키가_32바이트가_아니면_서버가_켜지지_않아요() {
		assertThatThrownBy(() -> new CryptoService(new CryptoProperties(""))).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new CryptoService(new CryptoProperties("짧은 키"))).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new CryptoService(new CryptoProperties(Base64.getEncoder().encodeToString(new byte[16]))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("32바이트");
	}
}
