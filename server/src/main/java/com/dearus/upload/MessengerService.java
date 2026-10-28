package com.dearus.upload;

import com.dearus.auth.User;
import com.dearus.auth.UserRepository;
import com.dearus.common.ApiException;
import com.dearus.common.Kst;
import com.dearus.common.Weeks;
import com.dearus.upload.WeeklySummarizer.DatedText;
import com.dearus.upload.WeeklySummarizer.WeekMessages;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메신저 업로드(가린 글자)를 받아요. 받은 글자는 서버에서 한 번 더 가리고 주 단위로 묶은 뒤, 요약은 백그라운드에서 해요.
 * 글자는 이 클래스 안에서 메모리에만 있다가 요약 작업(MessengerSummaryJob)으로 넘어가고, DB에는 한 번도 쓰지 않아요.
 */
@Service
public class MessengerService {

	private static final Logger log = LoggerFactory.getLogger(MessengerService.class);

	/** 요약이 이 시간 안에 안 끝나면 실패로 봐요(서버가 다시 켜져서 작업이 사라진 경우 포함). */
	static final Duration STALE_AFTER = Duration.ofMinutes(10);
	/** 요청 전체 크기 한도(초안). Content-Length는 필터가, 글자 크기는 여기서 한 번 더 봐요. */
	static final long MAX_TEXT_BYTES = 1_048_576;
	/** 한 업로드에서 요약할 수 있는 주 수(약 1년). AI 호출 수와 비용을 묶어 둬요. */
	static final int MAX_WEEKS = 60;

	private final UserRepository users;
	private final MessengerUploadRepository uploads;
	private final MessengerWeeklySummaryRepository summaries;
	private final SensitiveMasker masker;
	private final MessengerSummaryJob job;
	private final Executor executor;

	public MessengerService(UserRepository users, MessengerUploadRepository uploads, MessengerWeeklySummaryRepository summaries,
		SensitiveMasker masker, MessengerSummaryJob job, @Qualifier("messengerExecutor") Executor executor) {
		this.users = users;
		this.uploads = uploads;
		this.summaries = summaries;
		this.masker = masker;
		this.job = job;
		this.executor = executor;
	}

	/** POST /uploads/messenger — 받아서 202로 바로 돌려주고 요약은 뒤에서 해요. */
	public MessengerUploadResponse accept(UUID userId, MessengerUploadRequest request) {
		if (!Boolean.TRUE.equals(request.consent())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CONSENT_REQUIRED", "대화 분석에 동의해 주세요.");
		}
		User user = users.findById(userId).orElseThrow(ApiException::unauthorized);
		if (!user.aiConsentActive()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "AI_CONSENT_REQUIRED", "AI 분석에 동의해야 쓸 수 있어요.");
		}
		if (request.userMaskedCount() > request.maskedCount()) {
			throw ApiException.validation("userMaskedCount", "maskedCount보다 클 수 없어요");
		}

		LocalDate today = Kst.today();
		long bytes = 0;
		for (MessengerMessage message : request.messages()) {
			if (message.date().isAfter(today)) {
				throw ApiException.validation("messages", "미래 날짜의 글이 있어요");
			}
			bytes += message.text().getBytes(StandardCharsets.UTF_8).length;
		}
		if (bytes > MAX_TEXT_BYTES) {
			throw payloadTooLarge();
		}

		// 가리고, 주(월요일 시작) 단위로 묶어요. 글자는 여기서부터 메모리에만 있어요.
		Map<LocalDate, List<DatedText>> byWeek = new TreeMap<>();
		int serverMasked = 0;
		LocalDate first = null;
		LocalDate last = null;
		for (MessengerMessage message : request.messages()) {
			SensitiveMasker.Result masked = masker.mask(message.text());
			serverMasked += masked.count();
			byWeek.computeIfAbsent(weekStart(message.date()), k -> new ArrayList<>())
				.add(new DatedText(message.date(), masked.text().strip()));
			if (first == null || message.date().isBefore(first)) {
				first = message.date();
			}
			if (last == null || message.date().isAfter(last)) {
				last = message.date();
			}
		}
		if (byWeek.size() > MAX_WEEKS) {
			throw ApiException.validation("messages", "한 번에 " + MAX_WEEKS + "주(약 1년)까지만 올릴 수 있어요");
		}
		List<WeekMessages> weeks = new ArrayList<>();
		byWeek.forEach((weekStart, lines) -> {
			lines.sort(Comparator.comparing(DatedText::date));
			weeks.add(new WeekMessages(weekStart, lines));
		});

		// 같은 사람이 요약이 끝나기 전에 또 보내면 AI 호출이 겹쳐서 비용이 늘어서 막아요
		if (uploads.existsByUserIdAndStatusAndUploadedAtAfter(userId, MessengerStatus.PROCESSING, Kst.now().minus(STALE_AFTER))) {
			throw new ApiException(HttpStatus.CONFLICT, "UPLOAD_IN_PROGRESS", "이전에 올린 대화를 아직 분석하고 있어요. 끝난 뒤에 다시 올려 주세요.");
		}

		MessengerUpload upload = uploads.saveAndFlush(MessengerUpload.receive(
			userId, request.source(), request.photoCount(), request.maskedCount(), request.userMaskedCount()));
		UUID uploadId = upload.getId();
		LocalDate periodStart = first;
		LocalDate periodEnd = last;
		int serverMaskedCount = serverMasked;
		try {
			executor.execute(() -> job.run(uploadId, weeks, serverMaskedCount, periodStart, periodEnd));
		} catch (RejectedExecutionException e) {
			log.error("요약 작업을 시작하지 못했어요 uploadId={}", uploadId, e);
			upload.fail("INTERNAL_ERROR");
			uploads.save(upload);
		}
		return MessengerUploadResponse.from(upload, null);
	}

	@Transactional
	public MessengerUploadListResponse list(UUID userId) {
		return new MessengerUploadListResponse(uploads.findByUserIdOrderByUploadedAtDesc(userId).stream()
			.map(this::toResponse).toList());
	}

	@Transactional
	public MessengerUploadResponse get(UUID userId, UUID uploadId) {
		return toResponse(uploads.findByIdAndUserId(uploadId, userId).orElseThrow(ApiException::notFound));
	}

	/** 이 업로드와 거기서 만든 주간 요약을 모두 지워요 (요약은 DB의 ON DELETE CASCADE). */
	@Transactional
	public void delete(UUID userId, UUID uploadId) {
		uploads.delete(uploads.findByIdAndUserId(uploadId, userId).orElseThrow(ApiException::notFound));
	}

	/** 조회할 때 10분이 지난 PROCESSING은 FAILED로 바꿔요. 앱이 영원히 기다리지 않게요. */
	private MessengerUploadResponse toResponse(MessengerUpload upload) {
		if (upload.isProcessing() && upload.getUploadedAt().isBefore(Kst.now().minus(STALE_AFTER))) {
			upload.fail("PROCESSING_TIMEOUT");
			uploads.save(upload);
		}
		Integer count = upload.getStatus() == MessengerStatus.DONE ? summaries.countByUploadId(upload.getId()) : null;
		return MessengerUploadResponse.from(upload, count);
	}

	static LocalDate weekStart(LocalDate date) {
		return Weeks.startOf(date);
	}

	private static ApiException payloadTooLarge() {
		return new ApiException(HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE", "한 번에 올릴 수 있는 크기를 넘었어요. 나눠서 올려 주세요.");
	}
}
