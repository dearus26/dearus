package com.dearus.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 모든 오류를 API 명세의 { code, message, details } 형태로 바꿔요. */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ErrorResponse> handleApi(ApiException e) {
		return ResponseEntity.status(e.getStatus())
			.body(new ErrorResponse(e.getCode(), e.getMessage(), e.getDetails()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException e) {
		List<Map<String, String>> fields = e.getBindingResult().getFieldErrors().stream()
			.map(f -> Map.of("field", f.getField(), "reason", String.valueOf(f.getDefaultMessage())))
			.toList();
		return validationFailed(Map.of("fields", fields));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
		return validationFailed(Map.of("fields", List.of(Map.of("field", "body", "reason", "요청 형식이 올바르지 않아요"))));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
		return validationFailed(Map.of("fields", List.of(Map.of("field", e.getParameterName(), "reason", "꼭 필요해요"))));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
		return validationFailed(Map.of("fields", List.of(Map.of("field", e.getName(), "reason", "형식이 올바르지 않아요"))));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("NOT_FOUND", "찾을 수 없어요.", null));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e) {
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
			.body(new ErrorResponse("METHOD_NOT_ALLOWED", "지원하지 않는 요청이에요.", null));
	}

	/** JSON이 아닌 본문(사진 업로드의 multipart 등). 메신저 업로드는 사진을 받지 않는다고 알려줘요. */
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
		if (request.getRequestURI().startsWith("/uploads/messenger")) {
			return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(new ErrorResponse("IMAGES_NOT_ACCEPTED",
				"사진은 받지 않아요. 앱이 폰 안에서 글자를 읽어 JSON으로 보내 주세요.", null));
		}
		return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
			.body(new ErrorResponse("UNSUPPORTED_MEDIA_TYPE", "JSON으로 보내 주세요.", null));
	}

	/** 유일 제약 충돌처럼 동시에 같은 값을 넣었을 때예요. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException e) {
		log.warn("데이터 제약 위반", e);
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(new ErrorResponse("CONFLICT", "같은 요청이 겹쳤어요. 잠시 뒤에 다시 해 주세요.", null));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
		log.error("예상하지 못한 오류", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(new ErrorResponse("INTERNAL_ERROR", "서버에 문제가 생겼어요. 잠시 뒤에 다시 해 주세요.", null));
	}

	private ResponseEntity<ErrorResponse> validationFailed(Map<String, Object> details) {
		return ResponseEntity.badRequest()
			.body(new ErrorResponse("VALIDATION_FAILED", "입력한 내용을 다시 확인해 주세요.", details));
	}
}
