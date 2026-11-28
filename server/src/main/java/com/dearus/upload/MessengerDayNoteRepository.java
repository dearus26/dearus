package com.dearus.upload;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessengerDayNoteRepository extends JpaRepository<MessengerDayNote, UUID> {

	/** 이 업로드들의 하루 기록 중 from ~ to(양 끝 포함)인 것이에요. 홈 캘린더가 날짜의 주인 업로드 것만 골라 써요. */
	List<MessengerDayNote> findByUploadIdInAndDayBetween(Collection<UUID> uploadIds, LocalDate from, LocalDate to);
}
