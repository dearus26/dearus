package com.dearus.chat;

import com.dearus.persona.PersonaContent;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * 세션의 첫 메시지(seq 1)인 페르소나의 첫 인사를 골라요.
 * 개인 모드는 미래의 나에게 건네는 인사(greetings), 커플 모드는 미래에서 찾아온 연인에게 건네는 인사(coupleGreetings)예요(2026.11.28).
 * 커플 인사가 없는 예전 분석이면 커플 기본 인사를 써요(개인 인사는 "미래의 나"라고 불러서 연인에게 어색해요).
 * 분석 프롬프트가 막아도 Haiku가 가끔 어기는 인사는 여기서 한 번 더 걸러요. 걸러서 남는 게 없으면 기본 인사예요.
 */
final class ChatGreetings {

	/** 기록이 전혀 없어서 말투를 모를 때 쓰는 기본 인사예요. 상대에게 무슨 얘기를 할지 묻지 않아요(상담하는 느낌이 나요, 2026.11.28). */
	static final List<String> DEFAULT = List.of(
		"어, 왔어? 오랜만이네.",
		"안녕! 잘 지냈어?",
		"헐 진짜 왔네? 신기하다.");

	/** 커플 모드의 기본 인사예요. */
	static final List<String> COUPLE_DEFAULT = List.of(
		"어, 왔어? 보니까 반갑네.",
		"오 왔구나! 갑자기 보니까 신기하다.",
		"어 왔네. 잘 지냈어?");

	/** 상대를 위로하거나 쉬라고 권하는 인사예요(과거의 나가 상담하는 것처럼 시작해요). 개인 · 커플 모두 버려요. */
	static final Pattern CONSOLING = Pattern.compile("좀\\s?쉬|쉬어|쉬자|숨\\s?좀|괜찮아\\?|힘내|고생\\s?(했|많)");
	/** 커플 인사에서 상황 설정을 말해 버린 인사예요("나 연인이랑 만나는 거 맞아?", "미래에서 왔어?"). */
	static final Pattern COUPLE_SETUP = Pattern.compile("연인|미래");

	private ChatGreetings() {
	}

	/** 분석이 만든 인사 후보 중 쓸 수 있는 것 하나를 무작위로 골라요. 분석이 없거나 쓸 수 있는 후보가 없으면 기본 인사예요. */
	static String pick(PersonaContent content, boolean couple) {
		List<String> usable = usable(content, couple);
		List<String> pool = usable.isEmpty() ? (couple ? COUPLE_DEFAULT : DEFAULT) : usable;
		return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
	}

	/** 모드에 맞는 후보에서 위로하는 인사와(커플이면) 상황 설정을 말한 인사를 뺀 것이에요. */
	static List<String> usable(PersonaContent content, boolean couple) {
		if (content == null) {
			return List.of();
		}
		List<String> candidates = couple ? content.coupleGreetings() : content.greetings();
		return candidates.stream()
			.filter(greeting -> !CONSOLING.matcher(greeting).find())
			.filter(greeting -> !couple || !COUPLE_SETUP.matcher(greeting).find())
			.toList();
	}
}
