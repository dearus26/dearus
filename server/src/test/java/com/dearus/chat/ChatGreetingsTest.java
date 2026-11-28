package com.dearus.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.dearus.persona.PersonaContent;
import java.util.List;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/** 첫 인사를 개인 모드 · 커플 모드에 맞게 고르는지 확인해요(2026.11.28). */
class ChatGreetingsTest {

	private final PersonaContent content = new PersonaContent("반말", List.of(), "학기 중", "시험", "피곤", List.of("피곤"),
		List.of("헐 미래의 나야?"), null, List.of(), List.of(), null, null, List.of("어 왔어? 보고 싶었어"));

	@RepeatedTest(5)
	void 개인_모드는_미래의_나에게_건네는_인사예요() {
		assertThat(ChatGreetings.pick(content, false)).isEqualTo("헐 미래의 나야?");
	}

	@RepeatedTest(5)
	void 커플_모드는_연인에게_건네는_인사예요() {
		assertThat(ChatGreetings.pick(content, true)).isEqualTo("어 왔어? 보고 싶었어");
	}

	@Test
	void 커플_인사가_없는_예전_분석이면_커플_기본_인사를_써요() {
		PersonaContent old = new PersonaContent("반말", List.of(), "학기 중", "시험", "피곤", List.of("피곤"), List.of("헐 미래의 나야?"));

		// 개인 인사는 상대를 "미래의 나"라고 불러서 연인에게 쓰지 않아요
		assertThat(ChatGreetings.COUPLE_DEFAULT).contains(ChatGreetings.pick(old, true));
		assertThat(ChatGreetings.COUPLE_DEFAULT).noneMatch(g -> g.contains("미래의 나"));
	}

	@RepeatedTest(5)
	void 커플_인사에서_상황_설정을_말한_인사는_버려요() {
		PersonaContent awkward = new PersonaContent("반말", List.of(), "학기 중", "시험", "피곤", List.of("피곤"), List.of("헐 미래의 나야?"),
			null, List.of(), List.of(), null, null, List.of("헐 나 연인이랑 만나는 거 맞아?", "미래에서 왔어? ㅋㅋ", "어 왔어? 나 과제 중이었는데 반갑다"));

		assertThat(ChatGreetings.pick(awkward, true)).isEqualTo("어 왔어? 나 과제 중이었는데 반갑다");
	}

	@RepeatedTest(5)
	void 위로하거나_쉬라고_권하는_인사는_버려요() {
		PersonaContent consoling = new PersonaContent("반말", List.of(), "학기 중", "시험", "피곤", List.of("피곤"),
			List.of("요즘 정신없지? 오늘은 좀 쉬고 있어?", "숨 좀 돌리자!", "헐 미래의 나야? 나 지금 과제 중"));

		assertThat(ChatGreetings.pick(consoling, false)).isEqualTo("헐 미래의 나야? 나 지금 과제 중");
	}

	@Test
	void 걸러서_남는_인사가_없으면_기본_인사예요() {
		PersonaContent allBad = new PersonaContent("반말", List.of(), "학기 중", "시험", "피곤", List.of("피곤"), List.of("좀 쉬어"),
			null, List.of(), List.of(), null, null, List.of("미래의 연인이 왔네"));

		assertThat(ChatGreetings.DEFAULT).contains(ChatGreetings.pick(allBad, false));
		assertThat(ChatGreetings.COUPLE_DEFAULT).contains(ChatGreetings.pick(allBad, true));
		// 기본 인사는 거르는 규칙에 걸리지 않아요
		assertThat(ChatGreetings.COUPLE_DEFAULT).noneMatch(g -> ChatGreetings.COUPLE_SETUP.matcher(g).find() || ChatGreetings.CONSOLING.matcher(g).find());
		assertThat(ChatGreetings.DEFAULT).noneMatch(g -> ChatGreetings.CONSOLING.matcher(g).find());
	}

	@Test
	void 분석이_없으면_모드에_맞는_기본_인사예요() {
		assertThat(ChatGreetings.DEFAULT).contains(ChatGreetings.pick(null, false));
		assertThat(ChatGreetings.COUPLE_DEFAULT).contains(ChatGreetings.pick(null, true));
		// 기본 인사는 상대에게 무슨 얘기를 할지 묻지 않아요(상담하는 느낌이 나요)
		assertThat(ChatGreetings.DEFAULT).noneMatch(g -> g.contains("무슨 얘기"));
	}
}
