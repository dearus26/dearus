package com.dearus.upload;

import com.dearus.common.Kst;
import com.dearus.diary.Emotion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * messenger_day_notes 테이블(V13, 2026.11.28). 메신저로 올린 하루의 AI 추천 감정과 한 줄 요약(암호화)이에요.
 * 업로드를 요약할 때 함께 만들고(MessengerSummaryJob), 업로드가 지워지면 DB가 함께 지워요.
 */
@Entity
@Table(name = "messenger_day_notes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessengerDayNote {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "upload_id", nullable = false, updatable = false)
	private UUID uploadId;

	@Column(name = "day", nullable = false, updatable = false)
	private LocalDate day;

	/** 쉼표로 이은 감정 이름(JOY,TIRED). 없으면 빈 문자열이에요. */
	@Column(name = "emotions", nullable = false, updatable = false)
	private String emotions;

	/** 한 줄 요약을 암호화한 값. 없으면 null이에요. */
	@Column(name = "line_enc", updatable = false)
	private byte[] lineEnc;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	public static MessengerDayNote create(UUID uploadId, LocalDate day, List<Emotion> emotions, byte[] lineEnc) {
		MessengerDayNote note = new MessengerDayNote();
		note.uploadId = uploadId;
		note.day = day;
		note.emotions = emotions.stream().map(Emotion::name).collect(Collectors.joining(","));
		note.lineEnc = lineEnc;
		note.createdAt = Kst.now();
		return note;
	}

	/** 저장된 감정 이름을 읽어요. 모르는 이름(예전 값)은 건너뛰어요. */
	public List<Emotion> emotionList() {
		if (emotions == null || emotions.isBlank()) {
			return List.of();
		}
		return Arrays.stream(emotions.split(","))
			.map(String::strip)
			.filter(name -> Arrays.stream(Emotion.values()).anyMatch(e -> e.name().equals(name)))
			.map(Emotion::valueOf)
			.toList();
	}
}
