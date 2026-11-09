package com.dearus.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 모든 오류를 API 명세의 { code, message, details } 형태로 바꿔요. */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
	private static final String IMAGES_PATH = "/uploads/messenger/images";

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ErrorResponse> handleApi(ApiException e) {
		return json(e.getStatus(), new ErrorResponse(e.getCode(), e.getMessage(), e.getDetails()));
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
		return json(HttpStatus.NOT_FOUND, new ErrorResponse("NOT_FOUND", "찾을 수 없어요.", null));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e) {
		return json(HttpStatus.METHOD_NOT_ALLOWED, new ErrorResponse("METHOD_NOT_ALLOWED", "지원하지 않는 요청이에요.", null));
	}

	/**
	 * 본문 형식이 그 API와 맞지 않아요. 스크린샷 API에 multipart가 아닌 본문을 보내면 UNSUPPORTED_IMAGE_TYPE,
	 * txt API에 사진(multipart 등)을 보내면 IMAGES_NOT_ACCEPTED로 알려줘요.
	 */
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
		if (IMAGES_PATH.equals(request.getRequestURI())) {
			return json(HttpStatus.UNSUPPORTED_MEDIA_TYPE, new ErrorResponse("UNSUPPORTED_IMAGE_TYPE",
				"스크린샷은 multipart/form-data로 보내 주세요.", null));
		}
		if (request.getRequestURI().startsWith("/uploads/messenger")) {
			return json(HttpStatus.UNSUPPORTED_MEDIA_TYPE, new ErrorResponse("IMAGES_NOT_ACCEPTED",
				"이 API는 txt에서 추린 글자(JSON)만 받아요. 스크린샷은 POST /uploads/messenger/images로 보내 주세요.", null));
		}
		return json(HttpStatus.UNSUPPORTED_MEDIA_TYPE, new ErrorResponse("UNSUPPORTED_MEDIA_TYPE", "JSON으로 보내 주세요.", null));
	}

	/** 멀티파트가 한 장 5MB나 요청 전체 50MB(application.yml)를 넘었어요. 본문을 끝까지 읽기 전에 멈춰요. */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ErrorResponse> handleUploadSize(MaxUploadSizeExceededException e) {
		return json(HttpStatus.CONTENT_TOO_LARGE, new ErrorResponse("PAYLOAD_TOO_LARGE", "한 번에 올릴 수 있는 크기를 넘었어요. 나눠서 올려 주세요.", null));
	}

	/** 멀티파트 본문을 읽지 못했어요(형식이 깨졌거나 중간에 끊겼어요). */
	@ExceptionHandler(MultipartException.class)
	ResponseEntity<ErrorResponse> handleMultipart(MultipartException e) {
		return validationFailed(Map.of("fields", List.of(Map.of("field", "body", "reason", "요청 형식이 올바르지 않아요"))));
	}

	/** 유일 제약 충돌처럼 동시에 같은 값을 넣었을 때예요. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException e) {
		log.warn("데이터 제약 위반", e);
		return json(HttpStatus.CONFLICT, new ErrorResponse("CONFLICT", "같은 요청이 겹쳤어요. 잠시 뒤에 다시 해 주세요.", null));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
		log.error("예상하지 못한 오류", e);
		return json(HttpStatus.INTERNAL_SERVER_ERROR, new ErrorResponse("INTERNAL_ERROR", "서버에 문제가 생겼어요. 잠시 뒤에 다시 해 주세요.", null));
	}

	private ResponseEntity<ErrorResponse> validationFailed(Map<String, Object> details) {
		return json(HttpStatus.BAD_REQUEST, new ErrorResponse("VALIDATION_FAILED", "입력한 내용을 다시 확인해 주세요.", details));
	}

	/**
	 * 오류 응답은 항상 JSON이에요. 내용 형식(Content-Type)을 직접 정해 두면 요청의 Accept(예: text/event-stream)와 상관없이
	 * 오류를 JSON으로 돌려줘요. 안 그러면 SSE 요청의 400 · 409가 406으로 바뀌어요.
	 */
	private static ResponseEntity<ErrorResponse> json(HttpStatus status, ErrorResponse body) {
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
	}
}
