package com.dearus.chat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 대화 시간을 재는 규칙이에요(2026.11.4 결정). 연속한 메시지 사이 간격을 더하되, 한 간격은 최대 {@link #MAX_GAP}까지만 인정해요.
 * 끝내지 않은 대화나 며칠에 걸쳐 이어간 대화도 실제로 대화한 시간만 잡혀요(자리를 비운 시간이 섞이지 않아요).
 * 오늘 대화 시간은 DB에서 같은 규칙으로 더해요(ChatMessageRepository.sumGapSeconds).
 */
public final class ChatDuration {

	/** 한 간격으로 인정하는 최대 시간. 읽고 생각하고 치는 시간이에요. */
	public static final Duration MAX_GAP = Duration.ofMinutes(5);

	private ChatDuration() {
	}

	/** 시각(seq 오름차순)의 간격을 MAX_GAP까지만 인정해서 더해요. 메시지가 하나뿐이면 0이에요. */
	public static Duration total(List<OffsetDateTime> times) {
		Duration sum = Duration.ZERO;
		for (int i = 1; i < times.size(); i++) {
			Duration gap = Duration.between(times.get(i - 1), times.get(i));
			if (gap.isNegative()) {
				continue;
			}
			sum = sum.plus(gap.compareTo(MAX_GAP) > 0 ? MAX_GAP : gap);
		}
		return sum;
	}

	/** 분으로 반올림해요. 아주 짧아도 대화를 했으면(0초 초과) 1분으로 보여줘요. */
	public static int minutes(Duration duration) {
		long seconds = duration.toSeconds();
		if (duration.isNegative() || duration.isZero()) {
			return 0;
		}
		return (int) Math.max(1, (seconds + 30) / 60);
	}
}
