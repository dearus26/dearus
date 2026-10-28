package com.dearus.persona;

import com.dearus.common.CryptoService;
import com.dearus.common.Kst;
import com.dearus.common.ai.AiProperties;
import com.dearus.persona.PersonaAnalyzer.DiaryView;
import com.dearus.persona.PersonaAnalyzer.Prompt;
import com.dearus.persona.PersonaAnalyzer.WeekView;
import com.dearus.persona.PersonaInput.DiaryEntry;
import com.dearus.persona.PersonaInput.WeekEntry;
import com.dearus.upload.SensitiveMasker;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 한 시점의 분석을 찾거나 만들어요. 기록을 모아 지문을 계산하고, 저장된 분석의 지문과 같으면 AI를 부르지 않고 재사용해요.
 * 다르면 일기를 가려서 AI로 분석하고 같은 행을 덮어써요. 분석에 실패하면 이전에 저장한 분석은 그대로 남아요.
 * 같은 시점의 분석이 동시에 여러 번 요청되면 하나만 하고 나머지는 그 결과를 기다려요(AI 호출과 비용이 겹치지 않게요).
 * 오래 걸려서 호출하는 쪽(POST /chats의 PREPARING)이 뒤에서 부르고, 단계는 progress로 알려줘요.
 */
@Service
public class PersonaAnalysisService {

	private static final Logger log = LoggerFactory.getLogger(PersonaAnalysisService.class);

	/** PREPARING 화면의 단계(API 명세의 preparingStep)예요. */
	public enum Step {
		COLLECT, MASK, ANALYZE, BUILD
	}

	/** content가 분석 결과, analysisId는 저장된 행이고, reused가 true면 AI를 부르지 않았어요. */
	public record Result(PersonaContent content, UUID analysisId, boolean reused) {
	}

	private final PersonaCollector collector;
	private final PersonaAnalysisRepository analyses;
	private final PersonaAnalyzer analyzer;
	private final CryptoService crypto;
	private final SensitiveMasker masker;
	private final AiProperties aiProperties;
	private final TransactionTemplate tx;
	private final ConcurrentHashMap<String, CompletableFuture<Result>> inFlight = new ConcurrentHashMap<>();

	public PersonaAnalysisService(PersonaCollector collector, PersonaAnalysisRepository analyses, PersonaAnalyzer analyzer,
		CryptoService crypto, SensitiveMasker masker, AiProperties aiProperties, TransactionTemplate tx) {
		this.collector = collector;
		this.analyses = analyses;
		this.analyzer = analyzer;
		this.crypto = crypto;
		this.masker = masker;
		this.aiProperties = aiProperties;
		this.tx = tx;
	}

	/**
	 * 기록이 하나도 없으면(NONE) AI를 부르지 않고 빈 값을 돌려줘요(대화는 기본 말투로 해요).
	 * AI 호출이 실패하면 AiException이 그대로 나가요.
	 */
	public Optional<Result> getOrCreate(UUID userId, PersonaScope scope, LocalDate anchor, Consumer<Step> progress) {
		progress.accept(Step.COLLECT);
		PersonaRange range = PersonaRange.of(scope, anchor, Kst.today());
		PersonaInput input = collector.collect(userId, range);
		if (input.level() == RecordLevel.NONE) {
			return Optional.empty();
		}

		String digest = digest(input);
		Result reused = reusable(userId, input, digest);
		if (reused != null) {
			return Optional.of(reused);
		}

		String key = userId + "|" + range.anchor() + "|" + scope.windowDays() + "|" + digest;
		CompletableFuture<Result> mine = new CompletableFuture<>();
		CompletableFuture<Result> running = inFlight.putIfAbsent(key, mine);
		if (running != null) {
			progress.accept(Step.ANALYZE);
			return Optional.of(await(running));
		}
		try {
			Result result = analyze(userId, input, digest, progress);
			mine.complete(result);
			return Optional.of(result);
		} catch (RuntimeException e) {
			mine.completeExceptionally(e);
			throw e;
		} finally {
			inFlight.remove(key, mine);
		}
	}

	/** 세션을 만들 때 DB만 읽어서 바로 알 수 있는 것이에요. reusable이 있으면 AI를 부르지 않고 바로 대화할 수 있어요. */
	public record Lookup(RecordLevel level, Result reusable) {
	}

	/**
	 * 고른 시점의 기록 수준과, 저장된 분석을 그대로 쓸 수 있는지만 봐요(AI를 부르지 않아서 빨라요).
	 * 기록이 없으면(NONE) reusable도 없고 분석도 필요 없어요. 날짜가 범위를 벗어나면 400 ANCHOR_OUT_OF_RANGE예요.
	 */
	public Lookup lookup(UUID userId, PersonaScope scope, LocalDate anchor) {
		PersonaInput input = collector.collect(userId, PersonaRange.of(scope, anchor, Kst.today()));
		RecordLevel level = input.level();
		return new Lookup(level, level == RecordLevel.NONE ? null : reusable(userId, input, digest(input)));
	}

	/** 저장된 분석의 지문이 이번 기록의 지문과 같으면 그 분석이고, 아니면 null이에요. */
	private Result reusable(UUID userId, PersonaInput input, String digest) {
		PersonaRange range = input.range();
		PersonaAnalysis existing = analyses.findByUserIdAndAnchorDateAndWindowDays(userId, range.anchor(),
			(short) range.scope().windowDays()).orElse(null);
		if (existing != null && existing.getSourceDigest().equals(digest)) {
			return new Result(read(existing), existing.getId(), true);
		}
		return null;
	}

	private Result analyze(UUID userId, PersonaInput input, String digest, Consumer<Step> progress) {
		PersonaRange range = input.range();
		progress.accept(Step.MASK);
		List<DiaryView> diaries = input.diaries().stream()
			.map(d -> new DiaryView(d.date(), d.emotions(), masker.mask(d.content()).text())).toList();
		List<WeekView> weeks = input.weeks().stream().map(w -> weekView(range.scope(), w)).toList();

		progress.accept(Step.ANALYZE);
		PersonaContent content = analyzer.analyze(new Prompt(range, diaries, weeks));

		progress.accept(Step.BUILD);
		byte[] encrypted = crypto.encrypt(content.toJson());
		UUID id = save(userId, input, digest, encrypted);
		log.info("시점 분석 완료 userId={} scope={} diaries={} weeks={}", userId, range.scope(), input.diaries().size(), input.weeks().size());
		return new Result(content, id, false);
	}

	/** 같은 시점의 행이 있으면 덮어쓰고 없으면 만들어요. 다른 서버가 동시에 만든 경우(유일 키 충돌)에는 그 행을 써요. */
	private UUID save(UUID userId, PersonaInput input, String digest, byte[] encrypted) {
		PersonaRange range = input.range();
		short windowDays = (short) range.scope().windowDays();
		try {
			return tx.execute(status -> {
				PersonaAnalysis row = analyses.findByUserIdAndAnchorDateAndWindowDays(userId, range.anchor(), windowDays).orElse(null);
				if (row == null) {
					row = PersonaAnalysis.create(userId, range.anchor(), range.scope(), encrypted, digest,
						input.diaries().size(), input.weeks().size(), aiProperties.model());
				} else {
					row.replace(encrypted, digest, input.diaries().size(), input.weeks().size(), aiProperties.model());
				}
				return analyses.save(row).getId();
			});
		} catch (DataIntegrityViolationException e) {
			return analyses.findByUserIdAndAnchorDateAndWindowDays(userId, range.anchor(), windowDays)
				.map(PersonaAnalysis::getId).orElseThrow(() -> e);
		}
	}

	/** 하루 단위(DAY)는 말투 분석(traits)만 풀고, 전후 2주(AROUND)는 요약 · 말투 · 대표 문장을 모두 풀어요. */
	private WeekView weekView(PersonaScope scope, WeekEntry week) {
		String traits = crypto.decrypt(week.traitsEnc());
		if (scope == PersonaScope.DAY) {
			return new WeekView(week.weekStart(), null, traits, null);
		}
		return new WeekView(week.weekStart(), crypto.decrypt(week.summaryEnc()), traits,
			PersonaAnalyzer.samplesFrom(crypto.decrypt(week.sampleEnc())));
	}

	private PersonaContent read(PersonaAnalysis row) {
		return PersonaContent.fromJson(crypto.decrypt(row.getAnalysisEnc()));
	}

	/**
	 * 분석에 쓴 기록과 분석 방식이 같은지 보는 지문이에요. 구간 안 일기와 메신저 요약의 id · 수정 시각,
	 * 프롬프트 버전, 모델 이름이 하나라도 달라지면 달라져요(일기를 고치거나 지우거나 캡처를 새로 올리거나 프롬프트를 바꾼 경우).
	 */
	String digest(PersonaInput input) {
		StringBuilder source = new StringBuilder("v").append(PersonaAnalyzer.PROMPT_VERSION)
			.append('|').append(aiProperties.model())
			.append('|').append(input.range().scope()).append('|').append(input.range().anchor());
		for (DiaryEntry diary : input.diaries()) {
			source.append(";d:").append(diary.id()).append(':').append(diary.updatedAt().toInstant());
		}
		for (WeekEntry week : input.weeks()) {
			source.append(";w:").append(week.id()).append(':').append(week.createdAt().toInstant());
		}
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.toString().getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 쓸 수 없어요", e);
		}
	}

	private static Result await(CompletableFuture<Result> running) {
		try {
			return running.join();
		} catch (CompletionException e) {
			if (e.getCause() instanceof RuntimeException cause) {
				throw cause;
			}
			throw e;
		}
	}
}
