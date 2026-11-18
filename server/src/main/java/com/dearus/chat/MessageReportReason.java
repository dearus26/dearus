package com.dearus.chat;

/** 답변 신고 사유(API 명세의 MessageReportRequest.reason, 2026.11.18 확정). */
public enum MessageReportReason {
	/** 상처 주는 말 */
	HURTFUL,
	/** 내 기록과 다른 내용 */
	INACCURATE,
	/** 위험하거나 해로운 내용 */
	HARMFUL,
	/** 개인정보가 드러남 */
	PRIVACY,
	/** 기타. 직접 적은 내용(detail)이 꼭 있어야 해요 */
	OTHER
}
