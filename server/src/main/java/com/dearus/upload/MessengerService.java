package com.dearus.upload;

import com.dearus.auth.User;
import com.dearus.auth.UserRepository;
import com.dearus.common.ApiException;
import com.dearus.common.Kst;
import com.dearus.common.Weeks;
import com.dearus.common.ai.AiImage;
import com.dearus.upload.WeeklySummarizer.DatedText;
import com.dearus.upload.WeeklySummarizer.WeekMessages;
import java.io.IOException;
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
import org.springframework.web.multipart.MultipartFile;

/**
 * 메신저 업로드를 받아요. 두 갈래예요(2026.11.8 결정, 한 번에 한 형식).
 * - txt: 앱이 폰 안에서 추린 내 말 글자(JSON)를 받아 주 단위로 묶고, 요약은 백그라운드에서 해요(MessengerSummaryJob).
 * - 스크린샷: 사진을 메모리에만 받아 백그라운드에서 AI로 내 말을 읽고 바로 버린 뒤, txt와 같은 요약으로 넘겨요(MessengerImageJob).
 * 글자는 가리지 않아요. 요약이 모두 끝나 DONE이 될 때 내 말 원문을 암호화해 업로드 행에 1년 보관해요. 실패하면 원문도 남기지 않아요.
 */
@Service
public class MessengerService {

	private static final Logger log = LoggerFactory.getLogger(MessengerService.class);

	/** 요약이 이 시간 안에 안 끝나면 실패로 봐요(서버가 다시 켜져서 작업이 사라진 경우 포함). */
	static final Duration STALE_AFTER = Duration.ofMinutes(10);
	/** txt 요청 전체 크기 한도(초안). Content-Length는 필터가, 글자 크기는 여기서 한 번 더 봐요. */
	static final long MAX_TEXT_BYTES = 1_048_576;
	/** 한 업로드에서 요약할 수 있는 주 수(약 1년). AI 호출 수와 비용을 묶어 둬요. */
	static final int MAX_WEEKS = 60;
	/** 스크린샷 한 번에 올릴 수 있는 장수와 크기(API 명세, 초안). 멀티파트 설정(application.yml)과 같은 값이에요. */
	static final int MAX_IMAGES = 100;
	static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
	static final long MAX_IMAGES_TOTAL_BYTES = 50L * 1024 * 1024;

	/** 주(월요일 시작) 단위로 묶은 결과예요. first · last는 가장 이른 · 늦은 날짜예요. */
	record Grouped(List<WeekMessages> weeks, LocalDate first, LocalDate last) {
	}

	private final UserRepository users;
	private final MessengerUploadRepository uploads;
	private final MessengerWeeklySummaryRepository summaries;
	private final MessengerSummaryJob job;
	private final MessengerImageJob imageJob;
	private final Executor executor;

	public MessengerService(UserRepository users, MessengerUploadRepository uploads, MessengerWeeklySummaryRepository summaries,
		MessengerSummaryJob job, MessengerImageJob imageJob, @Qualifier("messengerExecutor") Executor executor) {
		this.users = users;
		this.uploads = uploads;
		this.summaries = summaries;
		this.job = job;
		this.imageJob = imageJob;
		this.executor = executor;
	}

	/** POST /uploads/messenger — txt에서 추린 내 말을 받아서 202로 바로 돌려주고 요약은 뒤에서 해요. */
	public MessengerUploadResponse accept(UUID userId, MessengerUploadRequest request) {
		checkCanUpload(userId, request.consent(), request.source());

		LocalDate today = Kst.today();
		long bytes = 0;
		List<DatedText> lines = new ArrayList<>(request.messages().size());
		for (MessengerMessage message : request.messages()) {
			if (message.date().isAfter(today)) {
				throw ApiException.validation("messages", "미래 날짜의 글이 있어요");
			}
			bytes += message.text().getBytes(StandardCharsets.UTF_8).length;
			lines.add(new DatedText(message.date(), message.text().strip()));
		}
		if (bytes > MAX_TEXT_BYTES) {
			throw payloadTooLarge();
		}
		Grouped grouped = groupByWeek(lines);
		if (grouped.weeks().size() > MAX_WEEKS) {
			throw ApiException.validation("messages", "한 번에 " + MAX_WEEKS + "주(약 1년)까지만 올릴 수 있어요");
		}
		checkNotInProgress(userId);

		MessengerUpload upload = uploads.saveAndFlush(MessengerUpload.receive(userId, request.source(), 0));
		UUID uploadId = upload.getId();
		start(upload, () -> job.run(uploadId, grouped.weeks(), grouped.first(), grouped.last()));
		return MessengerUploadResponse.from(upload, null);
	}

	/**
	 * POST /uploads/messenger/images — 카카오톡 스크린샷(대화 순서대로)을 받아서 202로 바로 돌려주고, 읽기와 요약은 뒤에서 해요.
	 * 사진은 디스크 · DB에 쓰지 않고 메모리에만 들고 있다가 뒤의 작업이 읽은 직후 버려요. 올릴 때 사용자 확인 단계는 없어요.
	 */
	public MessengerUploadResponse acceptImages(UUID userId, MessengerSource source, Boolean consent, List<MultipartFile> files) {
		if (source == null) {
			throw ApiException.validation("source", "꼭 필요해요");
		}
		if (files == null || files.isEmpty() || files.size() > MAX_IMAGES) {
			throw ApiException.validation("images", "이미지는 1장 이상 " + MAX_IMAGES + "장 이하예요");
		}
		checkCanUpload(userId, consent, source);
		List<AiImage> images = readImages(files);
		checkNotInProgress(userId);

		MessengerUpload upload = uploads.saveAndFlush(MessengerUpload.receive(userId, source, images.size()));
		UUID uploadId = upload.getId();
		start(upload, () -> imageJob.run(uploadId, images));
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

	/** 이 업로드(내 말 원문 포함)와 거기서 만든 주간 요약을 모두 지워요 (요약은 DB의 ON DELETE CASCADE). */
	@Transactional
	public void delete(UUID userId, UUID uploadId) {
		uploads.delete(uploads.findByIdAndUserId(uploadId, userId).orElseThrow(ApiException::notFound));
	}

	/** 업로드 동의(올릴 때마다) → AI 분석 동의 → 메신저 종류 순서로 확인해요. txt와 스크린샷이 같은 규칙이에요. */
	private void checkCanUpload(UUID userId, Boolean consent, MessengerSource source) {
		if (!Boolean.TRUE.equals(consent)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "CONSENT_REQUIRED", "대화 분석에 동의해 주세요.");
		}
		User user = users.findById(userId).orElseThrow(ApiException::unauthorized);
		if (!user.aiConsentActive()) {
			throw new ApiException(HttpStatus.FORBIDDEN, "AI_CONSENT_REQUIRED", "AI 분석에 동의해야 쓸 수 있어요.");
		}
		if (source != MessengerSource.KAKAOTALK) {
			throw ApiException.validation("source", "지금은 카카오톡만 올릴 수 있어요");
		}
	}

	/** 같은 사람이 요약이 끝나기 전에 또 보내면 AI 호출이 겹쳐서 비용이 늘어서 막아요. */
	private void checkNotInProgress(UUID userId) {
		if (uploads.existsByUserIdAndStatusAndUploadedAtAfter(userId, MessengerStatus.PROCESSING, Kst.now().minus(STALE_AFTER))) {
			throw new ApiException(HttpStatus.CONFLICT, "UPLOAD_IN_PROGRESS", "이전에 올린 대화를 아직 분석하고 있어요. 끝난 뒤에 다시 올려 주세요.");
		}
	}

	/**
	 * 받은 파일을 메모리의 이미지로 바꿔요(올린 순서 그대로). 형식은 파일 앞부분으로 알아내고(앱이 보낸 Content-Type은 보지 않아요),
	 * JPEG · PNG · WebP가 아니면 415 UNSUPPORTED_IMAGE_TYPE, 한 장 5MB나 전체 50MB를 넘으면 413이에요.
	 * 돌려주는 목록은 뒤의 작업이 읽은 직후 비울 수 있게 바꿀 수 있는 목록이에요.
	 */
	private static List<AiImage> readImages(List<MultipartFile> files) {
		List<AiImage> images = new ArrayList<>(files.size());
		long total = 0;
		for (MultipartFile file : files) {
			if (file == null || file.isEmpty()) {
				throw ApiException.validation("images", "비어 있는 파일이 있어요");
			}
			total += file.getSize();
			if (file.getSize() > MAX_IMAGE_BYTES || total > MAX_IMAGES_TOTAL_BYTES) {
				throw payloadTooLarge();
			}
			byte[] data;
			try {
				data = file.getBytes();
			} catch (IOException e) {
				throw ApiException.validation("images", "파일을 읽지 못했어요");
			}
			AiImage.MediaType type = AiImage.MediaType.detect(data).orElseThrow(MessengerService::unsupportedImage);
			images.add(new AiImage(type, data));
		}
		return images;
	}

	/** 업로드 행을 만든 뒤 뒤의 작업을 시작해요. 작업 줄이 가득 차서 못 넣으면 바로 FAILED로 바꿔요. */
	private void start(MessengerUpload upload, Runnable work) {
		try {
			executor.execute(work);
		} catch (RejectedExecutionException e) {
			log.error("메신저 작업을 시작하지 못했어요 uploadId={}", upload.getId(), e);
			upload.fail("INTERNAL_ERROR");
			uploads.save(upload);
		}
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

	/** 주(월요일 시작) 단위로 묶어요. 같은 날 안에서는 받은 순서를 지켜요(정렬이 안정적이에요). */
	static Grouped groupByWeek(List<DatedText> lines) {
		Map<LocalDate, List<DatedText>> byWeek = new TreeMap<>();
		LocalDate first = null;
		LocalDate last = null;
		for (DatedText line : lines) {
			byWeek.computeIfAbsent(weekStart(line.date()), k -> new ArrayList<>()).add(line);
			if (first == null || line.date().isBefore(first)) {
				first = line.date();
			}
			if (last == null || line.date().isAfter(last)) {
				last = line.date();
			}
		}
		List<WeekMessages> weeks = new ArrayList<>();
		byWeek.forEach((weekStart, week) -> {
			week.sort(Comparator.comparing(DatedText::date));
			weeks.add(new WeekMessages(weekStart, week));
		});
		return new Grouped(weeks, first, last);
	}

	static LocalDate weekStart(LocalDate date) {
		return Weeks.startOf(date);
	}

	private static ApiException payloadTooLarge() {
		return new ApiException(HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE", "한 번에 올릴 수 있는 크기를 넘었어요. 나눠서 올려 주세요.");
	}

	private static ApiException unsupportedImage() {
		return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_IMAGE_TYPE", "JPEG · PNG · WebP 이미지만 올릴 수 있어요.");
	}
}
