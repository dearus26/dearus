package com.dearus.chat;

/**
 * 사용자가 한 말에서 위기 신호(스스로를 해치려는 표현)를 찾아요. 정교한 방식(AI 판단 등)으로 바꿀 수 있게 인터페이스로 두었어요.
 */
public interface SafetyDetector {

	boolean detects(String text);
}
