package com.dearus.couple;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 초대 코드의 형식 · 글자 · 읽기 규칙을 확인해요. */
class InviteCodesTest {

	@Test
	void 코드는_DU_뒤에_4자리씩_두_묶음이고_헷갈리는_글자를_쓰지_않아요() {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 500; i++) {
			String code = InviteCodes.generate();
			assertThat(code).matches(InviteCodes.FORMAT);
			assertThat(code.substring(3).replace("-", "")).doesNotContain("0", "O", "1", "I");
			seen.add(code);
		}
		assertThat(seen).hasSizeGreaterThan(495); // 거의 겹치지 않아요
	}

	@Test
	void 소문자와_앞뒤_공백은_고쳐서_읽고_형식이_틀리면_null이에요() {
		assertThat(InviteCodes.normalize("  du-7k4q-2m9x ")).isEqualTo("DU-7K4Q-2M9X");
		assertThat(InviteCodes.normalize("DU-7K4Q2M9X")).isNull();
		assertThat(InviteCodes.normalize("XX-7K4Q-2M9X")).isNull();
		assertThat(InviteCodes.normalize(null)).isNull();
	}
}
