package com.dearus.common.ai;

/** AI를 부르다 실패했어요. code는 업로드의 failureCode나 오류 응답에 그대로 써요. */
public class AiException extends RuntimeException {

	public enum Reason {
		/** 키가 없거나, 네트워크 · 서버 문제로 AI에 닿지 못했어요 */
		UNAVAILABLE("AI_UNAVAILABLE"),
		/** AI의 안전 장치가 요청을 거절했어요 (서버 쪽 자동 대체 모델이 없어요) */
		REFUSED("AI_REFUSED"),
		/** 응답이 비었거나 잘렸거나 약속한 형태가 아니에요 */
		BAD_RESPONSE("AI_BAD_RESPONSE");

		private final String code;

		Reason(String code) {
			this.code = code;
		}

		public String code() {
			return code;
		}
	}

	private final Reason reason;

	public AiException(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	public AiException(Reason reason, String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}
}
