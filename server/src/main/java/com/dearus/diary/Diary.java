package com.dearus.diary;

import com.dearus.common.Kst;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** diaries + diary_emotions 테이블 (ERD). 일기는 하루 한 건이에요. */
@Entity
@Table(name = "diaries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Diary {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(name = "diary_date", nullable = false, updatable = false)
	private LocalDate diaryDate;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@ElementCollection
	@CollectionTable(name = "diary_emotions", joinColumns = @JoinColumn(name = "diary_id"))
	@Column(name = "emotion", nullable = false, length = 20)
	@Enumerated(EnumType.STRING)
	private Set<Emotion> emotions = new HashSet<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	public static Diary create(UUID userId, LocalDate date, String content, Set<Emotion> emotions) {
		Diary diary = new Diary();
		diary.userId = userId;
		diary.diaryDate = date;
		diary.content = content;
		diary.emotions.addAll(emotions);
		diary.createdAt = Kst.now();
		diary.updatedAt = diary.createdAt;
		return diary;
	}

	/** 날짜는 바꾸지 않고 본문과 감정 태그만 통째로 바꿔요. */
	public void update(String content, Set<Emotion> emotions) {
		this.content = content;
		this.emotions.clear();
		this.emotions.addAll(emotions);
		this.updatedAt = Kst.now();
	}
}
