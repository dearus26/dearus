package com.dearus.diary;

/** 감정 태그. 스토리보드의 7개예요. (선언 순서가 응답에서 정렬 순서가 돼요) label은 AI에 보낼 때 쓰는 한국어 이름이에요. */
public enum Emotion {
	JOY("기쁨"),
	EXCITED("설렘"),
	CALM("평온"),
	TIRED("피곤"),
	SAD("슬픔"),
	ANXIOUS("불안"),
	ANGRY("화남");

	private final String label;

	Emotion(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
