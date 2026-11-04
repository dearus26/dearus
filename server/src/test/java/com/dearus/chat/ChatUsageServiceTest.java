package com.dearus.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dearus.auth.User;
import com.dearus.auth.UserRepository;
import com.dearus.common.ApiException;
import com.dearus.common.Kst;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

/** 오늘 대화 시간: 조회 구간(한국 시간 오늘), 간격 상한, 분 변환, 권장 시간을 확인해요. */
class ChatUsageServiceTest {

	private final UserRepository users = mock(UserRepository.class);
	private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
	private final UUID userId = UUID.randomUUID();
	private ChatUsageService service;

	@BeforeEach
	void setUp() {
		when(users.findById(userId)).thenReturn(Optional.of(User.create("dev", "tester", "테스터")));
		service = new ChatUsageService(users, messages, new UsageProperties(30));
	}

	@Test
	void 오늘_구간의_초를_분으로_바꾸고_권장_시간을_함께_줘요() {
		when(messages.sumGapSeconds(eq(userId), any(), any(), anyInt())).thenReturn(725.0);

		UsageResponse usage = service.today(userId);

		assertThat(usage.date()).isEqualTo(Kst.today());
		assertThat(usage.chatMinutes()).isEqualTo(12);
		assertThat(usage.recommendedMinutes()).isEqualTo(30);
	}

	@Test
	void 한국_시간_오늘_0시부터_내일_0시까지_5분_상한으로_물어봐요() {
		when(messages.sumGapSeconds(eq(userId), any(), any(), anyInt())).thenReturn(0.0);

		service.today(userId);

		ArgumentCaptor<OffsetDateTime> from = ArgumentCaptor.forClass(OffsetDateTime.class);
		ArgumentCaptor<OffsetDateTime> to = ArgumentCaptor.forClass(OffsetDateTime.class);
		verify(messages).sumGapSeconds(eq(userId), from.capture(), to.capture(), eq(300));
		assertThat(from.getValue().atZoneSameInstant(Kst.ZONE).toLocalDate()).isEqualTo(Kst.today());
		assertThat(from.getValue().atZoneSameInstant(Kst.ZONE).toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
		assertThat(to.getValue()).isEqualTo(from.getValue().plusDays(1));
	}

	@Test
	void 오늘_대화가_없으면_0분이에요() {
		when(messages.sumGapSeconds(eq(userId), any(), any(), anyInt())).thenReturn(null);

		assertThat(service.today(userId).chatMinutes()).isZero();
	}

	@Test
	void 탈퇴한_계정의_토큰은_로그인이_필요해요() {
		UUID gone = UUID.randomUUID();
		when(users.findById(gone)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.today(gone))
			.isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
	}

	@Test
	void 권장_시간_설정이_비어_있으면_30분이에요() {
		assertThat(new UsageProperties(0).recommendedMinutes()).isEqualTo(30);
		assertThat(new UsageProperties(45).recommendedMinutes()).isEqualTo(45);
	}
}
