package com.dearus.chat;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageReportRepository extends JpaRepository<MessageReport, UUID> {

	boolean existsByMessageIdAndUserId(Long messageId, UUID userId);
}
