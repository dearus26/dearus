package com.dearus.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dearus.chat.ChatSessionRepository;
import com.dearus.common.Kst;
import com.dearus.persona.PersonaAnalysisRepository;
import com.dearus.upload.MessengerUploadRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 동의 철회 뒤 30일이 지난 사람의 분석 데이터와 대화 기록만 지우는지 확인해요(일기는 건드리지 않아요). */
class ConsentPurgeJobTest {

	private final UserRepository users = mock(UserRepository.class);
	private final ChatSessionRepository chatSessions = mock(ChatSessionRepository.class);
	private final PersonaAnalysisRepository personaAnalyses = mock(PersonaAnalysisRepository.class);
	private final MessengerUploadRepository messengerUploads = mock(MessengerUploadRepository.class);
	private final ConsentPurgeJob job = new ConsentPurgeJob(users, chatSessions, personaAnalyses, messengerUploads,
		new TransactionTemplate(mock(PlatformTransactionManager.class)));
	private final OffsetDateTime now = Kst.now();

	private User revokedDaysAgo(long days) {
		User user = User.create("dev", "x-" + UUID.randomUUID(), "시험");
		ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
		user.agree(false);
		user.revokeAiConsent();
		ReflectionTestUtils.setField(user, "aiRevokedAt", now.minusDays(days));
		return user;
	}

	private void expectDeletedFor(User user) {
		verify(chatSessions).deleteAllByUserId(user.getId());
		verify(personaAnalyses).deleteAllByUserId(user.getId());
		verify(messengerUploads).deleteAllByUserId(user.getId());
	}

	private void expectNothingDeleted() {
		verify(chatSessions, never()).deleteAllByUserId(any());
		verify(personaAnalyses, never()).deleteAllByUserId(any());
		verify(messengerUploads, never()).deleteAllByUserId(any());
	}

	@Test
	void 철회한_지_30일이_지난_사람의_대화와_분석과_메신저를_지워요() {
		User user = revokedDaysAgo(31);
		when(users.findIdsRevokedBefore(any())).thenReturn(List.of(user.getId()));
		when(users.findById(user.getId())).thenReturn(Optional.of(user));

		assertThat(job.purgeExpired(now)).isEqualTo(1);

		expectDeletedFor(user);
	}

	@Test
	void 보관_기준일은_지금에서_30일_전이에요() {
		when(users.findIdsRevokedBefore(any())).thenReturn(List.of());
		ArgumentCaptor<OffsetDateTime> captor = ArgumentCaptor.forClass(OffsetDateTime.class);

		assertThat(job.purgeExpired(now)).isZero();

		verify(users).findIdsRevokedBefore(captor.capture());
		assertThat(captor.getValue()).isEqualTo(now.minusDays(30));
		expectNothingDeleted();
	}

	@Test
	void 철회한_지_30일이_안_됐으면_지우지_않아요() {
		User user = revokedDaysAgo(29);
		when(users.findIdsRevokedBefore(any())).thenReturn(List.of(user.getId()));
		when(users.findById(user.getId())).thenReturn(Optional.of(user));

		assertThat(job.purgeExpired(now)).isZero();

		expectNothingDeleted();
	}

	@Test
	void 지우기_직전에_다시_동의했으면_지우지_않아요() {
		User user = revokedDaysAgo(40);
		user.agree(null); // 목록을 뽑은 뒤 다시 동의했어요
		when(users.findIdsRevokedBefore(any())).thenReturn(List.of(user.getId()));
		when(users.findById(user.getId())).thenReturn(Optional.of(user));

		assertThat(user.aiConsentActive()).isTrue();
		assertThat(job.purgeExpired(now)).isZero();

		expectNothingDeleted();
	}

	@Test
	void 그_사이_탈퇴했으면_건너뛰어요() {
		UUID gone = UUID.randomUUID();
		when(users.findIdsRevokedBefore(any())).thenReturn(List.of(gone));
		when(users.findById(gone)).thenReturn(Optional.empty());

		assertThat(job.purgeExpired(now)).isZero();

		expectNothingDeleted();
	}

	@Test
	void 한_사람이_실패해도_다른_사람은_계속_지워요() {
		User first = revokedDaysAgo(31);
		User second = revokedDaysAgo(45);
		when(users.findIdsRevokedBefore(any())).thenReturn(List.of(first.getId(), second.getId()));
		when(users.findById(first.getId())).thenReturn(Optional.of(first));
		when(users.findById(second.getId())).thenReturn(Optional.of(second));
		doThrow(new IllegalStateException("DB 오류")).when(chatSessions).deleteAllByUserId(first.getId());

		assertThat(job.purgeExpired(now)).isEqualTo(1); // 둘째만 지웠어요

		verify(chatSessions).deleteAllByUserId(second.getId());
		verify(personaAnalyses).deleteAllByUserId(second.getId());
		verify(messengerUploads).deleteAllByUserId(second.getId());
	}

	@Test
	void 정해진_시각에_도는_일은_오류가_나도_서버를_멈추지_않아요() {
		when(users.findIdsRevokedBefore(any())).thenThrow(new IllegalStateException("DB에 닿지 못했어요"));

		job.purgeDaily(); // 예외가 밖으로 나오지 않아야 해요
	}
}
