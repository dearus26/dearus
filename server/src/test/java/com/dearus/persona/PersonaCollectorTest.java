package com.dearus.persona;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dearus.common.CryptoProperties;
import com.dearus.common.CryptoService;
import com.dearus.common.Kst;
import com.dearus.common.Weeks;
import com.dearus.diary.Diary;
import com.dearus.diary.DiaryRepository;
import com.dearus.diary.Emotion;
import com.dearus.persona.PersonaInput.OriginalEntry;
import com.dearus.persona.PersonaInput.WeekEntry;
import com.dearus.upload.MessengerDayNote;
import com.dearus.upload.MessengerDayNoteRepository;
import com.dearus.upload.MessengerOriginal;
import com.dearus.upload.MessengerSource;
import com.dearus.upload.MessengerUpload;
import com.dearus.upload.MessengerUploadRepository;
import com.dearus.upload.MessengerWeeklySummary;
import com.dearus.upload.MessengerWeeklySummaryRepository;
import com.dearus.upload.WeeklySummarizer.DatedText;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 메신저를 하루치씩 올릴 때 날짜마다 그날을 담은 가장 최근 업로드를 쓰는 규칙(2026.11.28)을 DB 없이 확인해요.
 * 업로드는 최근 순, 요약은 주 오름차순 · 같은 주는 최근 순으로 돌려주는 저장소 쿼리를 흉내 내요.
 */
class PersonaCollectorTest {

	private final DiaryRepository diaries = mock(DiaryRepository.class);
	private final MessengerWeeklySummaryRepository summaries = mock(MessengerWeeklySummaryRepository.class);
	private final MessengerUploadRepository uploads = mock(MessengerUploadRepository.class);
	private final MessengerDayNoteRepository dayNotes = mock(MessengerDayNoteRepository.class);
	private final CryptoService crypto = new CryptoService(new CryptoProperties(Base64.getEncoder().encodeToString(new byte[32])));
	private final PersonaCollector collector = new PersonaCollector(diaries, summaries, uploads, dayNotes, crypto);
	private final UUID userId = UUID.randomUUID();

	/** 60일쯤 전 주의 월요일 · 화요일 · 수요일 */
	private final LocalDate mon = Weeks.startOf(Kst.today().minusDays(60));
	private final LocalDate tue = mon.plusDays(1);
	private final LocalDate wed = mon.plusDays(2);

	/** 최근 업로드가 앞이에요 */
	private final List<MessengerUpload> uploaded = new ArrayList<>();
	private final List<MessengerWeeklySummary> summaryRows = new ArrayList<>();
	private final Map<UUID, byte[]> originals = new HashMap<>();
	private final List<Diary> written = new ArrayList<>();

	@BeforeEach
	void setUp() {
		when(diaries.findByUserIdAndDiaryDateBetweenOrderByDiaryDateAsc(eq(userId), any(), any())).thenAnswer(call -> {
			LocalDate from = call.getArgument(1);
			LocalDate to = call.getArgument(2);
			return written.stream().filter(d -> !d.getDiaryDate().isBefore(from) && !d.getDiaryDate().isAfter(to)).toList();
		});
		when(uploads.findCovering(eq(userId), any(), any(), any(), any())).thenAnswer(call -> List.copyOf(uploaded));
		when(uploads.findOriginal(any())).thenAnswer(call -> originals.get(call.<UUID>getArgument(0)));
		when(summaries.findUsable(eq(userId), any(), any(), any())).thenAnswer(call -> List.copyOf(summaryRows));
	}

	/** 업로드 하나를 올려요. 나중에 부른 것이 더 최근 업로드예요(목록 앞에 넣어요). */
	private UUID upload(LocalDate periodStart, LocalDate periodEnd, DatedText... lines) {
		MessengerUpload upload = MessengerUpload.receive(userId, MessengerSource.KAKAOTALK, 0);
		UUID id = UUID.randomUUID();
		ReflectionTestUtils.setField(upload, "id", id);
		upload.complete(periodStart, periodEnd);
		uploaded.add(0, upload);
		originals.put(id, crypto.encrypt(MessengerOriginal.toJson(List.of(lines))));
		MessengerWeeklySummary row = MessengerWeeklySummary.create(id, Weeks.startOf(periodStart), crypto.encrypt("요약 " + periodStart),
			crypto.encrypt("말투 " + periodStart), crypto.encrypt("[]"), upload.getExpiresAt());
		ReflectionTestUtils.setField(row, "id", UUID.randomUUID());
		summaryRows.add(0, row); // 같은 주 안에서는 최근 요약이 앞이에요
		return id;
	}

	private PersonaInput collect(PersonaScope scope, LocalDate anchor) {
		return collector.collect(userId, PersonaRange.of(scope, anchor, Kst.today()));
	}

	@Test
	void 같은_주에_따로_올린_날은_모두_쓰여요() {
		UUID monUpload = upload(mon, mon, new DatedText(mon, "월요일 한 말"));
		UUID wedUpload = upload(wed, wed, new DatedText(wed, "수요일 한 말"));

		PersonaInput input = collect(PersonaScope.AROUND, mon);

		assertThat(input.weeks()).extracting(WeekEntry::from).containsExactly(wed, mon);
		assertThat(input.messengerWeeks()).isEqualTo(1); // 요약은 둘이어도 한 주예요
		assertThat(input.originals()).extracting(OriginalEntry::uploadId).containsExactlyInAnyOrder(monUpload, wedUpload);
		assertThat(input.originals()).filteredOn(o -> o.uploadId().equals(monUpload)).singleElement()
			.extracting(OriginalEntry::dates).isEqualTo(Set.of(mon));
	}

	@Test
	void 같은_날을_다시_올리면_새_업로드만_쓰여요() {
		upload(mon, mon, new DatedText(mon, "처음 올린 말"));
		UUID again = upload(mon, mon, new DatedText(mon, "다시 올린 말"));

		PersonaInput input = collect(PersonaScope.AROUND, mon);

		assertThat(input.weeks()).hasSize(1);
		assertThat(input.originals()).extracting(OriginalEntry::uploadId).containsExactly(again);
	}

	@Test
	void 하루_단위는_그날_올린_말이_있으면_LOW이고_그날을_올린_업로드의_말투만_붙여요() {
		UUID monUpload = upload(mon, mon, new DatedText(mon, "월요일 한 말"));
		upload(wed, wed, new DatedText(wed, "수요일 한 말"));

		PersonaInput input = collect(PersonaScope.DAY, mon);

		assertThat(input.level()).isEqualTo(RecordLevel.LOW);
		assertThat(input.messengerDays()).isEqualTo(1);
		assertThat(input.weeks()).singleElement().extracting(WeekEntry::from).isEqualTo(mon);
		assertThat(input.originals()).singleElement().satisfies(o -> {
			assertThat(o.uploadId()).isEqualTo(monUpload);
			assertThat(o.dates()).containsExactly(mon);
		});
	}

	@Test
	void 날마다의_한_줄_요약은_그날_주인_업로드_것만_가져와요() {
		UUID oldUpload = upload(mon, mon, new DatedText(mon, "예전 말"));
		UUID newUpload = upload(mon, mon, new DatedText(mon, "다시 올린 말"));
		when(dayNotes.findByUploadIdInAndDayBetween(any(), any(), any())).thenReturn(List.of(
			MessengerDayNote.create(oldUpload, mon, List.of(), crypto.encrypt("예전 요약")),
			MessengerDayNote.create(newUpload, mon, List.of(), crypto.encrypt("새 요약"))));

		PersonaInput input = collect(PersonaScope.DAY, mon);

		assertThat(input.dayLines()).singleElement().satisfies(line -> {
			assertThat(line.date()).isEqualTo(mon);
			assertThat(crypto.decrypt(line.lineEnc())).isEqualTo("새 요약");
		});
	}

	@Test
	void 하루_단위는_같은_주의_다른_날만_올렸으면_NONE이에요() {
		upload(mon, mon, new DatedText(mon, "월요일 한 말"));
		upload(wed, wed, new DatedText(wed, "수요일 한 말"));

		PersonaInput input = collect(PersonaScope.DAY, tue);

		assertThat(input.level()).isEqualTo(RecordLevel.NONE);
		assertThat(input.weeks()).isEmpty();
		assertThat(input.originals()).isEmpty();
	}

	@Test
	void 하루_단위는_올린_기간_안이어도_그날_말이_없으면_NONE이에요() {
		upload(mon, wed, new DatedText(mon, "월요일 한 말"), new DatedText(wed, "수요일 한 말"));

		assertThat(collect(PersonaScope.DAY, tue).level()).isEqualTo(RecordLevel.NONE);
		assertThat(collect(PersonaScope.DAY, wed).level()).isEqualTo(RecordLevel.LOW);
	}

	@Test
	void 하루_단위에_일기만_있으면_그_주의_가장_최근_말투를_더해요() {
		upload(mon, mon, new DatedText(mon, "월요일 한 말"));
		upload(wed, wed, new DatedText(wed, "수요일 한 말"));
		written.add(Diary.create(userId, tue, "화요일 일기", Set.of(Emotion.CALM)));

		PersonaInput input = collect(PersonaScope.DAY, tue);

		assertThat(input.level()).isEqualTo(RecordLevel.ENOUGH);
		assertThat(input.weeks()).singleElement().extracting(WeekEntry::from).isEqualTo(wed);
		assertThat(input.originals()).isEmpty(); // 그날 올린 말은 없어요
	}
}
