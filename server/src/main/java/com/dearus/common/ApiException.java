package com.dearus.common;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** 화면에 그대로 보여줄 수 있는 오류. { code, message, details } 형태로 내려가요. */
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;
	private final Map<String, Object> details;

	public ApiException(HttpStatus status, String code, String message) {
		this(status, code, message, null);
	}

	public ApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
		super(message);
		this.status = status;
		this.code = code;
		this.details = details;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}

	public Map<String, Object> getDetails() {
		return details;
	}

	/** 400 VALIDATION_FAILED. details.fields에 항목별 이유가 들어가요. */
	public static ApiException validation(String field, String reason) {
		return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "입력한 내용을 다시 확인해 주세요.",
			Map.of("fields", List.of(Map.of("field", field, "reason", reason))));
	}

	/** 404 NOT_FOUND. 남의 데이터도 같은 응답이라 존재 여부를 알 수 없어요. */
	public static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "찾을 수 없어요.");
	}

	public static ApiException unauthorized() {
		return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 만료됐어요. 다시 로그인해 주세요.");
	}
}
