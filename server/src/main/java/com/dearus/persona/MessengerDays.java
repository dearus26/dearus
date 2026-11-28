package com.dearus.persona;

import com.dearus.common.CryptoService;
import com.dearus.common.Kst;
import com.dearus.upload.MessengerOriginal;
import com.dearus.upload.MessengerStatus;
import com.dearus.upload.MessengerUpload;
import com.dearus.upload.MessengerUploadRepository;
import com.dearus.upload.WeeklySummarizer.DatedText;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 한 사람이 메신저(카톡 txt)로 내 말을 올린 날들이에요. 시점 분석(PersonaCollector)과 같은 규칙이에요(2026.11.28).
 * 날짜마다 그날을 담은(올린 기간에 그날이 들어 있는) 가장 최근 업로드가 그날의 주인이고, 그 업로드 원문에 그날 쓴 말이 실제로 있어야 해요.
 * 커플 모드의 함께 볼 날 고르기가 "메신저만 올린 날도 기록이 있는 날"로 보려고 써요. 원문을 풀어 보지만 날짜만 돌려주고 글은 내보내지 않아요.
 */
@Component
public class MessengerDays {

	private final MessengerUploadRepository uploads;
	private final CryptoService crypto;

	public MessengerDays(MessengerUploadRepository uploads, CryptoService crypto) {
		this.uploads = uploads;
		this.crypto = crypto;
	}

	/** from ~ to(양 끝 포함) 중 그날 올린 내 말이 있는 날이에요. */
	@Transactional(readOnly = true)
	public Set<LocalDate> withLines(UUID userId, LocalDate from, LocalDate to) {
		// 최근 업로드부터라 먼저 차지한 업로드가 그날의 주인이에요
		Map<LocalDate, UUID> ownerByDate = new LinkedHashMap<>();
		for (MessengerUpload upload : uploads.findCovering(userId, from, to, Kst.now(), MessengerStatus.DONE)) {
			LocalDate start = upload.getPeriodStart().isAfter(from) ? upload.getPeriodStart() : from;
			LocalDate end = upload.getPeriodEnd().isBefore(to) ? upload.getPeriodEnd() : to;
			for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
				ownerByDate.putIfAbsent(date, upload.getId());
			}
		}
		Map<UUID, Set<LocalDate>> datesByUpload = ownerByDate.entrySet().stream()
			.collect(Collectors.groupingBy(Map.Entry::getValue, LinkedHashMap::new,
				Collectors.mapping(Map.Entry::getKey, Collectors.toCollection(TreeSet::new))));

		Set<LocalDate> result = new TreeSet<>();
		datesByUpload.forEach((uploadId, owned) -> {
			byte[] originalEnc = uploads.findOriginal(uploadId);
			if (originalEnc == null) {
				return; // 원문이 없는 예전 업로드는 어느 날 말이 있는지 알 수 없어서 세지 않아요
			}
			for (DatedText line : MessengerOriginal.fromJson(crypto.decrypt(originalEnc))) {
				if (owned.contains(line.date()) && !line.text().isBlank()) {
					result.add(line.date());
				}
			}
		});
		return result;
	}
}
