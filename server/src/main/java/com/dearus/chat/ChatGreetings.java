package com.dearus.chat;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** 세션의 첫 메시지(seq 1)인 페르소나의 첫 인사를 골라요. */
final class ChatGreetings {

	/** 기록이 전혀 없어서 말투를 모를 때 쓰는 기본 인사예요. */
	static final List<String> DEFAULT = List.of(
		"어, 왔어? 오랜만이네.",
		"안녕! 잘 지냈어?",
		"어 왔구나. 무슨 얘기 하고 싶어?");

	private ChatGreetings() {
	}

	/** 분석이 만든 인사 후보 중 하나를 무작위로 골라요(후보가 비었으면 기본 인사). */
	static String pick(List<String> candidates) {
		List<String> pool = candidates == null || candidates.isEmpty() ? DEFAULT : candidates;
		return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
	}
}
