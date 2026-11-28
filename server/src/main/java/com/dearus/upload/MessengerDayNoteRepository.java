package com.dearus.upload;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessengerDayNoteRepository extends JpaRepository<MessengerDayNote, UUID> {

	/** 이 업로드들의 하루 기록 중 from ~ to(양 끝 포함)인 것이에요. 홈 캘린더가 날짜의 주인 업로드 것만 골라 써요. */
	List<MessengerDayNote> findByUploadIdInAndDayBetween(Collection<UUID> uploadIds, LocalDate from, LocalDate to);

	/** 이 업로드의 하루 기록이 하나라도 있는지예요(예전 업로드 채우기가 같은 업로드를 두 번 채우지 않게요). */
	boolean existsByUploadId(UUID uploadId);
}
