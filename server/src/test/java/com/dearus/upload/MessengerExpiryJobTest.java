package com.dearus.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dearus.common.Kst;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** DB 없이 1년 만료 삭제 작업의 규칙(지금 시각 기준으로 지우기 · 개수 돌려주기 · 실패해도 멈추지 않기)을 확인해요. */
class MessengerExpiryJobTest {

	private final MessengerUploadRepository uploads = mock(MessengerUploadRepository.class);
	private final MessengerExpiryJob job = new MessengerExpiryJob(uploads, new TransactionTemplate(mock(PlatformTransactionManager.class)));

	@Test
	void 지금_시각보다_만료가_이른_업로드를_지우고_개수를_돌려줘요() {
		OffsetDateTime now = Kst.now();
		when(uploads.deleteExpired(now)).thenReturn(3);

		assertThat(job.deleteExpired(now)).isEqualTo(3);
		verify(uploads).deleteExpired(now);
	}

	@Test
	void 지울_것이_없으면_0이에요() {
		when(uploads.deleteExpired(any())).thenReturn(0);

		assertThat(job.deleteExpired(Kst.now())).isZero();
	}

	@Test
	void 매일_도는_작업은_실패해도_예외를_밖으로_내보내지_않아요() {
		when(uploads.deleteExpired(any())).thenThrow(new IllegalStateException("DB에 닿지 못했어요"));

		job.deleteDaily(); // 예외가 나가면 이 시험이 실패해요

		verify(uploads).deleteExpired(any());
	}
}
