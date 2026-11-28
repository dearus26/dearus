package com.dearus.common;

import com.dearus.auth.ConsentPurgeJob;
import com.dearus.upload.DayNoteBackfillJob;
import com.dearus.upload.MessengerExpiryJob;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 서버가 켜질 때 새벽 삭제 작업(1년 만료 · 동의 철회 30일 폐기)을 한 번 더 돌려요.
 * Render 무료 요금제는 15분 동안 요청이 없으면 서버가 잠들어서 새벽 4시 작업을 건너뛸 수 있어요(2026.11.18 무료로 시작).
 * 두 작업은 여러 번 돌려도 결과가 같아서, 깨어날 때마다 밀린 삭제를 따라잡아요.
 * 배포 서버에서만 켜요(app.jobs.run-on-startup, 환경변수 JOBS_RUN_ON_STARTUP). 각자 PC에서는 꺼 둬요.
 * 예전 업로드의 하루 기록 채우기(DayNoteBackfillJob, 2026.11.28)도 켜질 때 뒤에서 돌려요. AI를 불러서 서버가 뜨는 걸 막지 않게 따로 돌아요.
 */
@Component
@ConditionalOnProperty(name = "app.jobs.run-on-startup", havingValue = "true")
public class StartupJobs {

	private static final Logger log = LoggerFactory.getLogger(StartupJobs.class);

	private final MessengerExpiryJob expiry;
	private final ConsentPurgeJob purge;
	private final DayNoteBackfillJob backfill;

	public StartupJobs(MessengerExpiryJob expiry, ConsentPurgeJob purge, DayNoteBackfillJob backfill) {
		this.expiry = expiry;
		this.purge = purge;
		this.backfill = backfill;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void catchUp() {
		log.info("서버가 켜져서 밀린 삭제 작업을 돌려요");
		// 두 메서드는 실패를 스스로 로그로 남기고 다음 날 다시 시도해요
		expiry.deleteDaily();
		purge.purgeDaily();
		CompletableFuture.runAsync(() -> {
			try {
				backfill.run();
			} catch (RuntimeException e) {
				log.error("예전 업로드의 하루 기록 채우기를 시작하지 못했어요", e);
			}
		});
	}
}
