package com.dearus.persona;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dearus.common.CryptoProperties;
import com.dearus.common.CryptoService;
import com.dearus.common.Kst;
import com.dearus.upload.MessengerOriginal;
import com.dearus.upload.MessengerSource;
import com.dearus.upload.MessengerUpload;
import com.dearus.upload.MessengerUploadRepository;
import com.dearus.upload.WeeklySummarizer.DatedText;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 메신저로 내 말을 올린 날을 시점 분석과 같은 규칙(날짜마다 최근 업로드, 그날 말이 실제로 있어야 함)으로 세는지 확인해요. */
class MessengerDaysTest {

	private final MessengerUploadRepository uploads = mock(MessengerUploadRepository.class);
	private final CryptoService crypto = new CryptoService(new CryptoProperties(Base64.getEncoder().encodeToString(new byte[32])));
	private final MessengerDays days = new MessengerDays(uploads, crypto);
	private final UUID userId = UUID.randomUUID();
	private final LocalDate d1 = Kst.today().minusDays(40);
	private final LocalDate d2 = d1.plusDays(1);
	private final LocalDate d3 = d1.plusDays(2);

	/** 최근 업로드가 앞이에요 */
	private final List<MessengerUpload> uploaded = new ArrayList<>();
	private final Map<UUID, byte[]> originals = new HashMap<>();

	@BeforeEach
	void setUp() {
		when(uploads.findCovering(eq(userId), any(), any(), any(), any())).thenAnswer(call -> List.copyOf(uploaded));
		when(uploads.findOriginal(any())).thenAnswer(call -> originals.get(call.<UUID>getArgument(0)));
	}

	private void upload(LocalDate start, LocalDate end, DatedText... lines) {
		MessengerUpload upload = MessengerUpload.receive(userId, MessengerSource.KAKAOTALK, 0);
		UUID id = UUID.randomUUID();
		ReflectionTestUtils.setField(upload, "id", id);
		upload.complete(start, end);
		uploaded.add(0, upload);
		if (lines.length > 0) {
			originals.put(id, crypto.encrypt(MessengerOriginal.toJson(List.of(lines))));
		}
	}

	@Test
	void 하루씩_올린_날을_모두_세요() {
		upload(d1, d1, new DatedText(d1, "월요일 말"));
		upload(d3, d3, new DatedText(d3, "수요일 말"));

		assertThat(days.withLines(userId, d1, d3)).containsExactly(d1, d3);
	}

	@Test
	void 올린_기간_안이어도_그날_말이_없으면_세지_않아요() {
		upload(d1, d3, new DatedText(d1, "첫날 말"), new DatedText(d3, "셋째 날 말"));

		assertThat(days.withLines(userId, d1, d3)).containsExactly(d1, d3);
	}

	@Test
	void 같은_날을_다시_올리면_최근_업로드로_봐요() {
		upload(d1, d2, new DatedText(d1, "예전 말"), new DatedText(d2, "예전 둘째 날"));
		// 최근 업로드가 d2를 차지했는데 d2의 말이 없으면 그날은 메신저 기록이 없어요(시점 분석과 같아요)
		upload(d2, d2, new DatedText(d2, "  "));

		assertThat(days.withLines(userId, d1, d2)).containsExactly(d1);
	}

	@Test
	void 원문이_없는_예전_업로드는_세지_않아요() {
		upload(d1, d1);

		assertThat(days.withLines(userId, d1, d1)).isEmpty();
	}
}
