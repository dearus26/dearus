package com.dearus.couple;

/**
 * couples.status (DB에 저장하는 값). 초대 코드를 기다리는 중(PENDING), 연결됨(CONNECTED), 철회 · 해제됨(DISCONNECTED)이에요.
 * API의 EXPIRED(코드가 24시간이 지남)와 NONE(연결 없음)은 저장하지 않고 응답을 만들 때 계산해요.
 */
public enum CoupleStatus {
	PENDING, CONNECTED, DISCONNECTED
}
