package com.dearus.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dearus.auth.User;
import com.dearus.auth.UserRepository;
import com.dearus.common.ApiException;
import com.dearus.common.CryptoProperties;
import com.dearus.common.CryptoService;
import com.dearus.common.Kst;
import com.dearus.common.ai.AiClient;
import com.dearus.common.ai.AiException;
import com.dearus.common.ai.AiRequest;
import com.dearus.common.ai.AiResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** DB · 실제 AI 없이 메신저 업로드(txt)의 규칙(동의 · 검증 · 주 묶기 · 원문 저장 · 성공 · 실패)을 확인해요. */
class MessengerServiceTest {

	private static final String GOOD = "{\"summary\":\"이번 주 요약\",\"traits\":\"말투 분석\",\"sample\":[\"문장 하나\"]}";

	private final UserRepository users = mock(UserRepository.class);
	private final MessengerUploadRepository uploads = mock(MessengerUploadRepository.class);
	private final MessengerWeeklySummaryRepository summaries = mock(MessengerWeeklySummaryRepository.class);
	private final AiClient ai = mock(AiClient.class);
	private final CryptoService crypto = new CryptoService(new CryptoProperties(Base64.getEncoder().encodeToString(new byte[32])));
	private final List<AiRequest> aiRequests = new ArrayList<>();
	private final UUID userId = UUID.randomUUID();
	private final UUID uploadId = UUID.randomUUID();
	private final List<MessengerWeeklySummary> savedSummaries = new ArrayList<>();
	private MessengerUpload saved;
	private MessengerService service;

	@BeforeEach
	void setUp() {
		User user = User.create("dev", "tester", "테스터");
		user.agree(false);
		when(users.findById(userId)).thenReturn(Optional.of(user));
		when(uploads.saveAndFlush(any())).thenAnswer(call -> {
			saved = call.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", uploadId); // DB에 저장되면 생기는 ID
			return saved;
		});
		when(uploads.findById(uploadId)).thenAnswer(call -> Optional.ofNullable(saved));
		when(uploads.save(any())).thenAnswer(call -> call.getArgument(0));
		when(summaries.save(any())).thenAnswer(call -> {
			savedSummaries.add(call.getArgument(0));
			return call.getArgument(0);
		});
		when(ai.complete(any())).thenAnswer(call -> {
			aiRequests.add(call.getArgument(0));
			return new AiResult(GOOD, 1, 1);
		});

		Executor direct = Runnable::run; // 뒤에서 하는 일을 테스트에서는 바로 실행해요
		TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
		MessengerSummaryJob job = new MessengerSummaryJob(new WeeklySummarizer(ai), crypto, uploads, summaries, tx, direct);
		service = new MessengerService(users, uploads, summaries, job, direct);
	}

	private MessengerUploadRequest request(MessengerMessage... messages) {
		return new MessengerUploadRequest(MessengerSource.KAKAOTALK, true, List.of(messages));
	}

	private MessengerMessage msg(String date, String text) {
		return new MessengerMessage(LocalDate.parse(date), text);
	}

	private ApiException apiError(Runnable action) {
		try {
			action.run();
		} catch (ApiException e) {
			return e;
		}
		throw new AssertionError("ApiException이 나야 하는데 나지 않았어요");
	}

	@Test
	void 동의_체크가_없으면_400_CONSENT_REQUIRED예요() {
		var request = new MessengerUploadRequest(MessengerSource.KAKAOTALK, false, List.of(msg("2026-09-28", "안녕")));

		ApiException e = apiError(() -> service.accept(userId, request));

		assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(e.getCode()).isEqualTo("CONSENT_REQUIRED");
		verify(uploads, never()).saveAndFlush(any());
	}

	@Test
	void AI_동의를_철회한_사람은_403이고_AI를_부르지_않아요() {
		User revoked = User.create("dev", "x", "x");
		revoked.agree(false);
		revoked.revokeAiConsent();
		when(users.findById(userId)).thenReturn(Optional.of(revoked));

		ApiException e = apiError(() -> service.accept(userId, request(msg("2026-09-28", "안녕"))));

		assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(e.getCode()).isEqualTo("AI_CONSENT_REQUIRED");
		verify(ai, never()).complete(any());
	}

	@Test
	void 카카오톡이_아니면_400이에요() {
		var request = new MessengerUploadRequest(MessengerSource.DISCORD, true, List.of(msg("2026-09-28", "안녕")));

		ApiException e = apiError(() -> service.accept(userId, request));

		assertThat(e.getCode()).isEqualTo("VALIDATION_FAILED");
		verify(uploads, never()).saveAndFlush(any());
	}

	@Test
	void 미래_날짜의_글이_있으면_400이에요() {
		ApiException e = apiError(() -> service.accept(userId, request(msg(Kst.today().plusDays(1).toString(), "내일"))));

		assertThat(e.getCode()).isEqualTo("VALIDATION_FAILED");
		verify(ai, never()).complete(any());
	}

	@Test
	void 글자_크기가_1MB를_넘으면_413이에요() {
		List<MessengerMessage> big = new ArrayList<>();
		for (int i = 0; i < 400; i++) {
			big.add(msg("2026-09-28", "가".repeat(1000))); // 한글은 3바이트라서 400 x 3000 = 1.2MB
		}
		var request = new MessengerUploadRequest(MessengerSource.KAKAOTALK, true, big);

		ApiException e = apiError(() -> service.accept(userId, request));

		assertThat(e.getStatus()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
	}

	@Test
	void 주가_60개를_넘으면_400이에요() {
		List<MessengerMessage> many = new ArrayList<>();
		LocalDate monday = LocalDate.of(2025, 11, 3);
		for (int i = 0; i < 61; i++) {
			many.add(new MessengerMessage(monday.plusWeeks(i), "글"));
		}
		var request = new MessengerUploadRequest(MessengerSource.KAKAOTALK, true, many);

		ApiException e = apiError(() -> service.accept(userId, request));

		assertThat(e.getCode()).isEqualTo("VALIDATION_FAILED");
	}

	@Test
	void 요약이_끝나기_전에_또_올리면_409예요() {
		when(uploads.existsByUserIdAndStatusAndUploadedAtAfter(any(), any(), any())).thenReturn(true);

		ApiException e = apiError(() -> service.accept(userId, request(msg("2026-09-28", "안녕"))));

		assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(e.getCode()).isEqualTo("UPLOAD_IN_PROGRESS");
		verify(uploads, never()).saveAndFlush(any());
	}

	@Test
	void 받은_글자를_가리지_않고_주_단위로_묶어_요약하고_원문과_함께_암호화해_저장해요() {
		// 2026-09-28(월) ~ 10-04(일)가 한 주, 10-05(월)부터 다음 주
		MessengerUploadResponse response = service.accept(userId, request(
			msg("2026-10-05", "내 번호 010-1234-5678 이야"),
			msg("2026-09-30", "오늘 피곤"),
			msg("2026-09-28", "월요일이다"),
			msg("2026-10-04", "일요일 ㅋㅋ")));

		// 접수 응답은 아직 PROCESSING이 아니라 직접 실행해서 DONE이 되었지만, 응답 객체는 접수 시점의 값이에요
		assertThat(response.id()).isEqualTo(uploadId);
		assertThat(response.format()).isEqualTo(MessengerFormat.TXT);
		assertThat(response.photoCount()).isZero();
		assertThat(saved.getStatus()).isEqualTo(MessengerStatus.DONE);
		assertThat(saved.getPeriodStart()).isEqualTo(LocalDate.of(2026, 9, 28));
		assertThat(saved.getPeriodEnd()).isEqualTo(LocalDate.of(2026, 10, 5));

		// 내 말 원문은 날짜 순으로 암호화돼 업로드 행에 저장돼요
		ArgumentCaptor<byte[]> original = ArgumentCaptor.forClass(byte[].class);
		verify(uploads).storeOriginal(org.mockito.ArgumentMatchers.eq(uploadId), original.capture());
		assertThat(MessengerOriginal.fromJson(crypto.decrypt(original.getValue()))).extracting(WeeklySummarizer.DatedText::text)
			.containsExactly("월요일이다", "오늘 피곤", "일요일 ㅋㅋ", "내 번호 010-1234-5678 이야");

		// 주마다 AI를 한 번씩, 첫 주 → 둘째 주 순서로 불러요
		assertThat(aiRequests).hasSize(2);
		String firstWeek = aiRequests.get(0).messages().get(0).text();
		String secondWeek = aiRequests.get(1).messages().get(0).text();
		assertThat(firstWeek).contains("week=\"2026-09-28\"").contains("09-28 월요일이다").contains("09-30 오늘 피곤").contains("10-04 일요일");
		assertThat(firstWeek.indexOf("09-28")).isLessThan(firstWeek.indexOf("09-30"));
		// 글자는 가리지 않아요(2026.11.8 결정)
		assertThat(secondWeek).contains("week=\"2026-10-05\"").contains("010-1234-5678");

		// 요약은 암호화돼서 저장되고, 풀면 원래 글이에요
		assertThat(savedSummaries).hasSize(2);
		MessengerWeeklySummary first = savedSummaries.get(0);
		assertThat(first.getUploadId()).isEqualTo(uploadId);
		assertThat(first.getWeekStart()).isEqualTo(LocalDate.of(2026, 9, 28));
		assertThat(crypto.decrypt(first.getSummaryEnc())).isEqualTo("이번 주 요약");
		assertThat(crypto.decrypt(first.getTraitsEnc())).isEqualTo("말투 분석");
		assertThat(crypto.decrypt(first.getSampleEnc())).isEqualTo("[\"문장 하나\"]");
		assertThat(first.getExpiresAt()).isEqualTo(saved.getExpiresAt());
	}

	@Test
	void AI가_실패하면_요약을_하나도_저장하지_않고_FAILED가_돼요() {
		when(ai.complete(any())).thenThrow(new AiException(AiException.Reason.UNAVAILABLE, "닿지 못했어요"));

		service.accept(userId, request(msg("2026-09-28", "안녕"), msg("2026-10-05", "다음 주")));

		assertThat(saved.getStatus()).isEqualTo(MessengerStatus.FAILED);
		assertThat(saved.getFailureCode()).isEqualTo("AI_UNAVAILABLE");
		assertThat(savedSummaries).isEmpty();
		verify(uploads, never()).storeOriginal(any(), any()); // 실패하면 원문도 남기지 않아요
	}

	@Test
	void 둘째_주에서_실패해도_첫_주만_저장되지_않아요() {
		when(ai.complete(any()))
			.thenReturn(new AiResult(GOOD, 1, 1))
			.thenThrow(new AiException(AiException.Reason.REFUSED, "거절"));

		service.accept(userId, request(msg("2026-09-28", "안녕"), msg("2026-10-05", "다음 주")));

		assertThat(saved.getStatus()).isEqualTo(MessengerStatus.FAILED);
		assertThat(saved.getFailureCode()).isEqualTo("AI_REFUSED");
		assertThat(savedSummaries).isEmpty();
	}

	@Test
	void AI의_답이_약속한_형태가_아니면_AI_BAD_RESPONSE로_실패해요() {
		when(ai.complete(any())).thenReturn(new AiResult("요약이에요", 1, 1));

		service.accept(userId, request(msg("2026-09-28", "안녕")));

		assertThat(saved.getStatus()).isEqualTo(MessengerStatus.FAILED);
		assertThat(saved.getFailureCode()).isEqualTo("AI_BAD_RESPONSE");
	}

	@Test
	void 요약하는_동안_업로드가_지워졌으면_아무것도_저장하지_않아요() {
		when(uploads.findById(uploadId)).thenReturn(Optional.empty());

		service.accept(userId, request(msg("2026-09-28", "안녕")));

		assertThat(savedSummaries).isEmpty();
		ArgumentCaptor<MessengerUpload> captor = ArgumentCaptor.forClass(MessengerUpload.class);
		verify(uploads, never()).save(captor.capture());
	}

	@Test
	void 요약이_10분_넘게_끝나지_않으면_조회할_때_FAILED로_바꿔요() {
		MessengerUpload stale = MessengerUpload.receive(userId, MessengerSource.KAKAOTALK, 0);
		ReflectionTestUtils.setField(stale, "id", uploadId);
		ReflectionTestUtils.setField(stale, "uploadedAt", Kst.now().minusMinutes(11));
		when(uploads.findByIdAndUserId(uploadId, userId)).thenReturn(Optional.of(stale));

		MessengerUploadResponse response = service.get(userId, uploadId);

		assertThat(response.status()).isEqualTo(MessengerStatus.FAILED);
		assertThat(response.failureCode()).isEqualTo("PROCESSING_TIMEOUT");
	}

	@Test
	void 남의_업로드는_404예요() {
		when(uploads.findByIdAndUserId(uploadId, userId)).thenReturn(Optional.empty());

		assertThat(apiError(() -> service.get(userId, uploadId)).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(apiError(() -> service.delete(userId, uploadId)).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void 주의_시작은_월요일이에요() {
		assertThat(MessengerService.weekStart(LocalDate.of(2026, 9, 28))).isEqualTo(LocalDate.of(2026, 9, 28)); // 월
		assertThat(MessengerService.weekStart(LocalDate.of(2026, 10, 4))).isEqualTo(LocalDate.of(2026, 9, 28)); // 일
		assertThat(MessengerService.weekStart(LocalDate.of(2026, 10, 5))).isEqualTo(LocalDate.of(2026, 10, 5)); // 월
	}

}
