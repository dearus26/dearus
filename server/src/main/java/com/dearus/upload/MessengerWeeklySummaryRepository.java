package com.dearus.upload;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessengerWeeklySummaryRepository extends JpaRepository<MessengerWeeklySummary, UUID> {

	int countByUploadId(UUID uploadId);

	List<MessengerWeeklySummary> findByUploadIdOrderByWeekStartAsc(UUID uploadId);
}
