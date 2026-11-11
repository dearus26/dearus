package com.dearus.upload;

import com.dearus.common.Kst;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 보관 기간 1년(messenger_uploads.expires_at)이 지난 메신저 업로드를 매일 지워요(2026.11.8 결정).
 * 업로드 행을 지우면 내 말 원문(original_enc)도 함께 지워지고, 주간 요약은 DB의 ON DELETE CASCADE로 지워져요.
 * 같은 일을 여러 번 해도 결과가 같아서 서버가 여러 대 켜져 있어도 괜찮아요. 로그에는 지운 개수만 남겨요.
 * 동의 철회 30일 뒤 폐기(ConsentPurgeJob)와는 따로 돌아요. 그쪽은 철회한 사람의 업로드를 기간과 상관없이 지워요.
 */
@Component
public class MessengerExpiryJob {

	private static final Logger log = LoggerFactory.getLogger(MessengerExpiryJob.class);

	private final MessengerUploadRepository uploads;
	private final TransactionTemplate tx;

	public MessengerExpiryJob(MessengerUploadRepository uploads, TransactionTemplate tx) {
		this.uploads = uploads;
		this.tx = tx;
	}

	/** 매일 새벽 4시(한국 시간)에 돌아요. 동의 철회 폐기(4시 30분)와 시간을 나눠 DB 부담을 겹치지 않게 해요. */
	@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
	public void deleteDaily() {
		try {
			deleteExpired(Kst.now());
		} catch (RuntimeException e) {
			// 실패하면 다음 날 다시 시도해요(지울 대상은 그대로 남아 있어요)
			log.error("보관 기간이 지난 메신저 업로드를 지우지 못했어요", e);
		}
	}

	/** now 기준으로 보관 기간이 끝난 업로드를 지우고, 지운 개수를 돌려줘요. */
	public int deleteExpired(OffsetDateTime now) {
		Integer deleted = tx.execute(status -> uploads.deleteExpired(now));
		int count = deleted == null ? 0 : deleted;
		if (count > 0) {
			log.info("보관 기간 1년이 지난 메신저 업로드를 지웠어요 uploads={}", count);
		}
		return count;
	}
}
