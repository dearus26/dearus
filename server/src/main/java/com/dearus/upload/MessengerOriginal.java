package com.dearus.upload;

import com.dearus.upload.WeeklySummarizer.DatedText;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;

/**
 * 내 말 원문(messenger_uploads.original_enc)을 암호화하기 전의 JSON으로 바꾸고 읽어요.
 * 모양은 [{"date":"2026-09-28","text":"..."}, ...]이고 날짜 오름차순이에요(같은 날은 받은 순서).
 */
public final class MessengerOriginal {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private MessengerOriginal() {
	}

	public static String toJson(List<DatedText> lines) {
		ArrayNode array = MAPPER.createArrayNode();
		for (DatedText line : lines) {
			array.addObject().put("date", line.date().toString()).put("text", line.text());
		}
		return MAPPER.writeValueAsString(array);
	}

	public static List<DatedText> fromJson(String json) {
		try {
			List<DatedText> lines = new ArrayList<>();
			for (JsonNode item : MAPPER.readTree(json)) {
				lines.add(new DatedText(LocalDate.parse(item.get("date").asString()), item.get("text").asString()));
			}
			return lines;
		} catch (RuntimeException e) { // JacksonException도 여기에 들어와요
			// 원문이 담긴 예외 메시지를 남기지 않아요
			throw new IllegalStateException("저장된 메신저 원문을 읽지 못했어요");
		}
	}
}
