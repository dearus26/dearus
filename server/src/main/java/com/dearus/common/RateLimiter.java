package com.dearus.common;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 1분 단위 고정 창으로 요청 수를 세요. 서버 한 대를 가정해서 메모리에 둬요(backlog 8장 "서버 한 대 가정").
 * 창이 바뀌면 새로 세고, 오래된 창은 쌓이지 않게 가끔 지워요.
 */
public class RateLimiter {

	static final long WINDOW_MILLIS = 60_000;
	private static final int CLEANUP_THRESHOLD = 10_000;

	private record Window(long index, int count) {
	}

	private final Map<String, Window> windows = new ConcurrentHashMap<>();
	private final LongSupplier clock;

	public RateLimiter() {
		this(System::currentTimeMillis);
	}

	RateLimiter(LongSupplier clock) {
		this.clock = clock;
	}

	/** 이번 요청을 세고, 상한 안이면 0을, 넘었으면 다음 창까지 남은 초(1 이상)를 돌려줘요. */
	public long acquire(String key, int limit) {
		long now = clock.getAsLong();
		long index = now / WINDOW_MILLIS;
		Window window = windows.merge(key, new Window(index, 1),
			(old, fresh) -> old.index() == index ? new Window(index, old.count() + 1) : fresh);
		if (windows.size() > CLEANUP_THRESHOLD) {
			windows.values().removeIf(w -> w.index() < index);
		}
		if (window.count() <= limit) {
			return 0;
		}
		long remainingMillis = (index + 1) * WINDOW_MILLIS - now;
		return Math.max(1, (remainingMillis + 999) / 1000);
	}
}
