package com.dearus.persona;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dearus.common.CryptoProperties;
import com.dearus.common.CryptoService;
import com.dearus.common.Kst;
import com.dearus.common.ai.AiClient;
import com.dearus.common.ai.AiException;
import com.dearus.common.ai.AiProperties;
import com.dearus.common.ai.AiRequest;
import com.dearus.common.ai.AiResult;
import com.dearus.diary.Emotion;
import com.dearus.persona.PersonaAnalysisService.Result;
import com.dearus.persona.PersonaAnalysisService.Step;
import com.dearus.persona.PersonaInput.DiaryEntry;
import com.dearus.persona.PersonaInput.WeekEntry;
import com.dearus.upload.SensitiveMasker;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** DB · 실제 AI 없이 시점 분석의 규칙(재사용 · 덮어쓰기 · 가림 · 동시 요청 · 실패)을 확인해요. */
class PersonaAnalysisServiceTest {

	private final PersonaCollector collector = mock(PersonaCollector.class);
	private final PersonaAnalysisRepository analyses = mock(PersonaAnalysisRepository.class);
	private final AiClient ai = mock(AiClient.class);
	private final CryptoService crypto = new CryptoService(new CryptoProperties(Base64.getEncoder().encodeToString(new byte[32])));
	private final AiProperties aiProperties = new AiProperties("anthropic", "", "claude-haiku-5-5", 90);
	private final UUID userId = UUID.randomUUID();
	private final LocalDate anchor = Kst.today().minusDays(60);
	private final List<AiRequest> aiRequests = new ArrayList<>();
	private final AtomicInteger aiCalls = new AtomicInteger();

	private PersonaInput current;
	private PersonaAnalysis stored;
	private PersonaAnalysisService service;

	@BeforeEach
	void setUp() {
		when(collector.collect(any(), any())).thenAnswer(call -> current);
		when(analyses.findByUserIdAndAnchorDateAndWindowDays(any(), any(), anyShort())).thenAnswer(call -> Optional.ofNullable(stored));
		when(analyses.save(any())).thenAnswer(call -> {
			stored = call.getArgument(0);
			if (stored.getId() == null) {
				ReflectionTestUtils.setField(stored, "id", UUID.randomUUID()); // DB에 저장되면 생기는 ID
			}
			return stored;
		});
		when(ai.complete(any())).thenAnswer(call -> {
			aiCalls.incrementAndGet();
			aiRequests.add(call.getArgument(0));
			return new AiResult(PersonaAnalyzerTest.GOOD, 1, 1);
		});
		service = newService(aiProperties);
		current = input(PersonaScope.AROUND, List.of(diary("시험이 걱정돼", 1), diary("오늘은 좀 쉬었다", 2)), List.of());
	}

	private PersonaAnalysisService newService(AiProperties properties) {
		return new PersonaAnalysisService(collector, analyses, new PersonaAnalyzer(ai), crypto, new SensitiveMasker(), properties,
			new TransactionTemplate(mock(PlatformTransactionManager.class)));
	}

	private PersonaInput input(PersonaScope scope, List<DiaryEntry> diaries, List<WeekEntry> weeks) {
		return new PersonaInput(PersonaRange.of(scope, anchor, Kst.today()), diaries, weeks);
	}

	private DiaryEntry diary(String content, int dayOffset) {
		return new DiaryEntry(UUID.randomUUID(), anchor.plusDays(dayOffset), Kst.now(), List.of(Emotion.TIRED), content);
	}

	private WeekEntry week(String summary, String traits, String samplesJson) {
		return new WeekEntry(UUID.randomUUID(), anchor.minusDays(3), Kst.now(), crypto.encrypt(summary), crypto.encrypt(traits),
			crypto.encrypt(samplesJson));
	}

	private Optional<Result> run(PersonaScope scope, List<Step> steps) {
		return service.getOrCreate(userId, scope, anchor, steps::add);
	}

	@Test
	void 기록이_없으면_AI를_부르지_않고_빈_값을_돌려줘요() {
		current = input(PersonaScope.AROUND, List.of(), List.of());

		assertThat(run(PersonaScope.AROUND, new ArrayList<>())).isEmpty();
		verify(ai, never()).complete(any());
		verify(analyses, never()).save(any());
	}

	@Test
	void 하루_단위는_메신저_말투_분석만_있고_그날_일기가_없으면_분석하지_않아요() {
		current = input(PersonaScope.DAY, List.of(), List.of(week("요약", "말투", "[\"문장\"]")));

		assertThat(run(PersonaScope.DAY, new ArrayList<>())).isEmpty();
		verify(ai, never()).complete(any());
	}

	@Test
	void 처음에는_AI로_분석해서_암호화해_저장하고_단계를_알려줘요() {
		List<Step> steps = new ArrayList<>();

		Result result = run(PersonaScope.AROUND, steps).orElseThrow();

		assertThat(steps).containsExactly(Step.COLLECT, Step.MASK, Step.ANALYZE, Step.BUILD);
		assertThat(result.reused()).isFalse();
		assertThat(result.content().voice()).isEqualTo("짧은 반말을 써요.");
		assertThat(result.analysisId()).isEqualTo(stored.getId());
		// 저장된 값은 암호화돼 있고 풀면 분석 결과예요
		assertThat(new String(stored.getAnalysisEnc(), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("voice");
		assertThat(PersonaContent.fromJson(crypto.decrypt(stored.getAnalysisEnc()))).isEqualTo(result.content());
		assertThat(stored.getWindowDays()).isEqualTo((short) 14);
		assertThat(stored.getDiaryCount()).isEqualTo(2);
		assertThat(stored.getModel()).isEqualTo("claude-haiku-5-5");
		assertThat(stored.getSourceDigest()).hasSize(64);
	}

	@Test
	void 기록이_같으면_AI를_다시_부르지_않고_재사용해요() {
		Result first = run(PersonaScope.AROUND, new ArrayList<>()).orElseThrow();
		List<Step> steps = new ArrayList<>();

		Result second = run(PersonaScope.AROUND, steps).orElseThrow();

		assertThat(aiCalls.get()).isEqualTo(1);
		assertThat(second.reused()).isTrue();
		assertThat(second.analysisId()).isEqualTo(first.analysisId());
		assertThat(second.content()).isEqualTo(first.content());
		assertThat(steps).containsExactly(Step.COLLECT);
	}

	@Test
	void 일기를_고치면_다시_분석해서_같은_행을_덮어써요() {
		Result first = run(PersonaScope.AROUND, new ArrayList<>()).orElseThrow();
		String oldDigest = stored.getSourceDigest();
		// 일기 하나를 고쳐서 updatedAt이 달라졌어요
		DiaryEntry edited = new DiaryEntry(current.diaries().get(0).id(), current.diaries().get(0).date(),
			current.diaries().get(0).updatedAt().plusMinutes(5), List.of(Emotion.TIRED), "시험이 끝났다");
		current = input(PersonaScope.AROUND, List.of(edited, current.diaries().get(1)), List.of());

		Result second = run(PersonaScope.AROUND, new ArrayList<>()).orElseThrow();

		assertThat(aiCalls.get()).isEqualTo(2);
		assertThat(second.reused()).isFalse();
		assertThat(second.analysisId()).isEqualTo(first.analysisId()); // 같은 행
		assertThat(stored.getSourceDigest()).isNotEqualTo(oldDigest);
	}

	@Test
	void 일기를_지워도_메신저_요약을_올려도_지문이_달라져요() {
		String base = service.digest(current);

		PersonaInput fewer = input(PersonaScope.AROUND, List.of(current.diaries().get(0)), List.of());
		PersonaInput more = input(PersonaScope.AROUND, current.diaries(), List.of(week("요약", "말투", "[\"문장\"]")));

		assertThat(service.digest(fewer)).isNotEqualTo(base);
		assertThat(service.digest(more)).isNotEqualTo(base);
		assertThat(service.digest(current)).isEqualTo(base); // 같은 기록이면 같아요
	}

	@Test
	void 모델이나_구간이_달라도_지문이_달라져요() {
		PersonaAnalysisService other = newService(new AiProperties("anthropic", "", "claude-sonnet-5-5", 90));

		assertThat(other.digest(current)).isNotEqualTo(service.digest(current));
		PersonaInput day = input(PersonaScope.DAY, current.diaries(), List.of());
		assertThat(service.digest(day)).isNotEqualTo(service.digest(current));
	}

	@Test
	void 일기는_가린_뒤에_AI로_보내요() {
		current = input(PersonaScope.AROUND, List.of(diary("내 번호 010-1234-5678 이야", 1)), List.of());

		run(PersonaScope.AROUND, new ArrayList<>());

		String sent = aiRequests.get(0).messages().get(0).text();
		assertThat(sent).contains("[전화번호]").doesNotContain("010-1234-5678");
	}

	@Test
	void 전후_2주는_메신저_요약_말투_대표_문장을_풀어서_보내요() {
		current = input(PersonaScope.AROUND, List.of(diary("일기", 1)), List.of(week("시험 준비로 바빴어요", "짧은 반말을 써요", "[\"오늘 피곤\",\"ㅋㅋ 그러게\"]")));

		run(PersonaScope.AROUND, new ArrayList<>());

		String sent = aiRequests.get(0).messages().get(0).text();
		assertThat(sent).contains("요약: 시험 준비로 바빴어요").contains("말투: 짧은 반말을 써요").contains("대표 문장: 오늘 피곤 | ㅋㅋ 그러게");
	}

	@Test
	void 하루_단위는_메신저_말투_분석만_풀어서_보내요() {
		current = input(PersonaScope.DAY, List.of(diary("오늘 화났다", 0)), List.of(week("그 주에 이사했어요", "짧은 반말을 써요", "[\"이사했어 ㅠㅠ\"]")));

		run(PersonaScope.DAY, new ArrayList<>());

		String sent = aiRequests.get(0).messages().get(0).text();
		assertThat(sent).contains("<messenger_voice").contains("짧은 반말을 써요");
		assertThat(sent).doesNotContain("그 주에 이사했어요").doesNotContain("이사했어 ㅠㅠ");
		assertThat(stored.getWindowDays()).isEqualTo((short) 0);
		assertThat(stored.getMessengerWeeks()).isEqualTo(1);
	}

	@Test
	void AI가_실패하면_이전에_저장한_분석은_그대로_남아요() {
		run(PersonaScope.AROUND, new ArrayList<>());
		PersonaAnalysis before = stored;
		byte[] beforeBytes = before.getAnalysisEnc();
		String beforeDigest = before.getSourceDigest();
		current = input(PersonaScope.AROUND, List.of(diary("새로 쓴 일기", 3)), List.of());
		doThrow(new AiException(AiException.Reason.UNAVAILABLE, "닿지 못했어요")).when(ai).complete(any());

		assertThatThrownBy(() -> run(PersonaScope.AROUND, new ArrayList<>()))
			.isInstanceOfSatisfying(AiException.class, e -> assertThat(e.reason()).isEqualTo(AiException.Reason.UNAVAILABLE));

		assertThat(stored).isSameAs(before);
		assertThat(stored.getAnalysisEnc()).isEqualTo(beforeBytes);
		assertThat(stored.getSourceDigest()).isEqualTo(beforeDigest);
	}

	@Test
	void AI의_답이_약속한_형태가_아니면_저장하지_않고_BAD_RESPONSE예요() {
		doReturn(new AiResult("분석했어요", 1, 1)).when(ai).complete(any());

		assertThatThrownBy(() -> run(PersonaScope.AROUND, new ArrayList<>()))
			.isInstanceOfSatisfying(AiException.class, e -> assertThat(e.reason()).isEqualTo(AiException.Reason.BAD_RESPONSE));
		assertThat(stored).isNull();
	}

	@Test
	void 같은_시점이_동시에_요청되면_AI는_한_번만_불러요() throws Exception {
		CountDownLatch aiStarted = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		doAnswer(call -> {
			aiCalls.incrementAndGet();
			aiStarted.countDown();
			release.await(5, TimeUnit.SECONDS);
			return new AiResult(PersonaAnalyzerTest.GOOD, 1, 1);
		}).when(ai).complete(any());
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<Optional<Result>> first = pool.submit(() -> run(PersonaScope.AROUND, new ArrayList<>()));
			assertThat(aiStarted.await(5, TimeUnit.SECONDS)).isTrue();
			Future<Optional<Result>> second = pool.submit(() -> run(PersonaScope.AROUND, new ArrayList<>()));
			Thread.sleep(300); // 두 번째 요청이 첫 번째를 기다리는 자리까지 가게요
			release.countDown();

			Result a = first.get(10, TimeUnit.SECONDS).orElseThrow();
			Result b = second.get(10, TimeUnit.SECONDS).orElseThrow();

			assertThat(aiCalls.get()).isEqualTo(1);
			assertThat(b.content()).isEqualTo(a.content());
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void 동시_요청의_첫_분석이_실패하면_기다리던_요청도_같은_오류를_받아요() throws Exception {
		CountDownLatch aiStarted = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		doAnswer(call -> {
			aiStarted.countDown();
			release.await(5, TimeUnit.SECONDS);
			throw new AiException(AiException.Reason.REFUSED, "거절");
		}).when(ai).complete(any());
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<Optional<Result>> first = pool.submit(() -> run(PersonaScope.AROUND, new ArrayList<>()));
			assertThat(aiStarted.await(5, TimeUnit.SECONDS)).isTrue();
			Future<Optional<Result>> second = pool.submit(() -> run(PersonaScope.AROUND, new ArrayList<>()));
			Thread.sleep(300);
			release.countDown();

			for (Future<Optional<Result>> f : List.of(first, second)) {
				assertThatThrownBy(() -> f.get(10, TimeUnit.SECONDS)).hasRootCauseInstanceOf(AiException.class);
			}
			// 실패한 요청이 끝났으니 다음 요청은 새로 시도할 수 있어요
			doReturn(new AiResult(PersonaAnalyzerTest.GOOD, 1, 1)).when(ai).complete(any());
			assertThat(run(PersonaScope.AROUND, new ArrayList<>())).isPresent();
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void 분석_요청의_구간이_범위를_벗어나면_400이에요() {
		assertThatThrownBy(() -> service.getOrCreate(userId, PersonaScope.DAY, Kst.today().minusDays(400), step -> { }))
			.hasMessageContaining("고를 수 없는 날짜");
		ArgumentCaptor<PersonaRange> captor = ArgumentCaptor.forClass(PersonaRange.class);
		verify(collector, never()).collect(any(), captor.capture());
	}
}
