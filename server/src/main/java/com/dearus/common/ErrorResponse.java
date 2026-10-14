package com.dearus.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** API 명세의 Error 스키마. details가 없으면 필드를 빼요. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, Map<String, Object> details) {
}
