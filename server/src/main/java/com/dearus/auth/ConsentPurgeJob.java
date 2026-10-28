package com.dearus.auth;

import com.dearus.chat.ChatSessionRepository;
import com.dearus.common.Kst;
import com.dearus.persona.PersonaAnalysisRepository;
import com.dearus.upload.MessengerUploadRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AI 분석 동의를 철회한 지 30일({@link ConsentRetention#AFTER_REVOKE})이 지난 사람의 분석 데이터와 대화 기록을 지워요.
 * 지우는 것: 대화 세션과 메시지, 시점 분석, 메신저 업로드와 주간 요약. 일기는 지우지 않아요.
 * 그 안에 다시 동의했으면 철회 시각이 비워져서 여기에 걸리지 않아요. 같은 일을 여러 번 해도 결과가 같아서(없으면 건너뛰어요)
 * 서버가 여러 대 켜져 있어도 괜찮아요. 로그에는 사용자 수만 남겨요.
 */
@Component
public class ConsentPurgeJob {

	private static final Logger log = LoggerFactory.getLogger(ConsentPurgeJob.class);

	private final UserRepository users;
	private final ChatSessionRepository chatSessions;
	private final PersonaAnalysisRepository personaAnalyses;
	private final MessengerUploadRepository messengerUploads;
	private final TransactionTemplate tx;

	public ConsentPurgeJob(UserRepository users, ChatSessionRepository chatSessions, PersonaAnalysisRepository personaAnalyses,
		MessengerUploadRepository messengerUploads, TransactionTemplate tx) {
		this.users = users;
		this.chatSessions = chatSessions;
		this.personaAnalyses = personaAnalyses;
		this.messengerUploads = messengerUploads;
		this.tx = tx;
	}

	/** 매일 새벽 4시 30분(한국 시간)에 돌아요. */
	@Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
	public void purgeDaily() {
		try {
			purgeExpired(Kst.now());
		} catch (RuntimeException e) {
			log.error("동의 철회 후 보관 기간이 지난 데이터 폐기에 실패했어요", e);
		}
	}

	/** now 기준으로 보관 기간이 끝난 사용자의 데이터를 지우고, 지운 사용자 수를 돌려줘요. */
	public int purgeExpired(OffsetDateTime now) {
		OffsetDateTime cutoff = now.minus(ConsentRetention.AFTER_REVOKE);
		int purged = 0;
		for (UUID userId : users.findIdsRevokedBefore(cutoff)) {
			try {
				if (purgeIfExpired(userId, now)) {
					purged++;
				}
			} catch (RuntimeException e) {
				// 한 사람이 실패해도 다른 사람은 계속 지워요. 실패한 사람은 다음 날 다시 시도해요.
				log.error("분석 데이터 폐기에 실패했어요 userId={}", userId, e);
			}
		}
		if (purged > 0) {
			log.info("동의 철회 후 보관 기간이 지나 분석 데이터와 대화 기록을 폐기했어요 users={}", purged);
		}
		return purged;
	}

	/**
	 * 한 사람의 보관 기간이 끝났으면 지우고 true를 돌려줘요. 지우기 직전에 다시 확인해요(그 사이 다시 동의하거나 탈퇴했을 수 있어요).
	 * 사용자마다 따로 트랜잭션을 써요. 한 사람만 지우고 싶을 때(시험 등)도 써요.
	 */
	public boolean purgeIfExpired(UUID userId, OffsetDateTime now) {
		OffsetDateTime cutoff = now.minus(ConsentRetention.AFTER_REVOKE);
		Boolean done = tx.execute(status -> {
			User user = users.findById(userId).orElse(null);
			if (user == null || user.aiConsentActive() || user.getAiRevokedAt() == null || !user.getAiRevokedAt().isBefore(cutoff)) {
				return false;
			}
			chatSessions.deleteAllByUserId(userId); // 메시지는 ON DELETE CASCADE
			personaAnalyses.deleteAllByUserId(userId);
			messengerUploads.deleteAllByUserId(userId); // 주간 요약은 ON DELETE CASCADE
			return true;
		});
		return Boolean.TRUE.equals(done);
	}
}
