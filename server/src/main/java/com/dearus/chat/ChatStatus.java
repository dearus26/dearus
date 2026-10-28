package com.dearus.chat;

/** 대화 세션의 상태. PREPARING은 시점 분석 중, READY는 대화할 수 있어요, FAILED는 분석에 실패했어요. */
public enum ChatStatus {
	PREPARING, READY, FAILED
}
