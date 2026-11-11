package com.dearus.couple;

import static org.assertj.core.api.Assertions.assertThat;

import com.dearus.chat.ChatSession;
import com.dearus.common.Kst;
import com.dearus.couple.CoupleComparisonService.Side;
import com.dearus.persona.PersonaScope;
import com.dearus.persona.RecordLevel;
import com.dearus.report.Report;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 두 시점 비교의 화면 상태(NOT_STARTED → IN_PROGRESS → WAITING_PARTNER → GENERATING → READY)를 확인해요. */
class CoupleComparisonServiceTest {

	private final UUID coupleId = UUID.randomUUID();
	private final UUID me = UUID.randomUUID();
	private final UUID partner = UUID.randomUUID();
	private final LocalDate day = Kst.today().minusDays(30);

	private ChatSession session(UUID userId, UUID ownerId, boolean ended) {
		ChatSession session = ChatSession.startCouple(userId, ownerId, coupleId, day, PersonaScope.DAY, null, RecordLevel.ENOUGH);
		session.ready(null);
		if (ended) {
			session.end(Kst.now());
		}
		return session;
	}

	private Report generating(ChatSession session) {
		return Report.generating(UUID.randomUUID(), 3, Kst.now());
	}

	private Report ready(String memory, String gap) {
		Report report = Report.generating(UUID.randomUUID(), 3, Kst.now());
		report.completeCouple(memory, "한 줄", Kst.now());
		if (gap != null) {
			report.setGap(gap, Kst.now());
		}
		return report;
	}

	@Test
	void 아직_대화하지_않았으면_NOT_STARTED이고_대화_중이면_IN_PROGRESS예요() {
		assertThat(CoupleComparisonService.status(null, null)).isEqualTo("NOT_STARTED");
		assertThat(CoupleComparisonService.status(new Side(session(me, partner, false), null), null)).isEqualTo("IN_PROGRESS");
	}

	@Test
	void 나만_끝냈으면_연인을_기다려요() {
		Side mine = new Side(session(me, partner, true), ready("그날의 도윤은 불안했다고 했어요", null));

		assertThat(CoupleComparisonService.status(mine, null)).isEqualTo("WAITING_PARTNER");
		assertThat(CoupleComparisonService.status(mine, new Side(session(partner, me, false), null))).isEqualTo("WAITING_PARTNER");
	}

	@Test
	void 둘_다_끝났어도_리포트나_간극이_없으면_GENERATING이에요() {
		ChatSession mineSession = session(me, partner, true);
		Side theirsDone = new Side(session(partner, me, true), ready("그날의 서연은 서운했다고 했어요", null));

		assertThat(CoupleComparisonService.status(new Side(mineSession, generating(mineSession)), theirsDone)).isEqualTo("GENERATING");
		assertThat(CoupleComparisonService.status(new Side(mineSession, ready("a", null)), theirsDone)).isEqualTo("GENERATING");
	}

	@Test
	void 두_리포트의_간극이_같아야_READY예요() {
		Side mine = new Side(session(me, partner, true), ready("a", "서운함은 침묵으로 전해졌어요."));

		assertThat(CoupleComparisonService.status(mine, new Side(session(partner, me, true), ready("b", "서운함은 침묵으로 전해졌어요."))))
			.isEqualTo("READY");
		// 연인이 같은 날 다시 대화해서 새 리포트에 간극이 아직 없으면 다시 만들어요
		assertThat(CoupleComparisonService.status(mine, new Side(session(partner, me, true), ready("c", null)))).isEqualTo("GENERATING");
	}
}
