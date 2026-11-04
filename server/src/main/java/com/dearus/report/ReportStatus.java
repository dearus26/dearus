package com.dearus.report;

/**
 * 리포트의 상태. GENERATING은 AI가 만드는 중, READY는 완성, FAILED는 실패(끝내기를 다시 부르면 재시도),
 * NONE은 내 말이 너무 적어서 만들지 않은 경우예요.
 */
public enum ReportStatus {
	GENERATING, READY, FAILED, NONE
}
