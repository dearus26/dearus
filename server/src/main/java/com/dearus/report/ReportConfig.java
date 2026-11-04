package com.dearus.report;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReportConfig {

	/**
	 * 리포트를 뒤에서 만드는 작업 줄이에요. 동시에 2건만 만들고 대기는 16개까지만 받아서 AI 호출이 한꺼번에 몰리지 않게 해요.
	 * 넘치면 끝내기가 503 SERVER_BUSY를 돌려주고, 그 리포트는 FAILED가 돼서 끝내기를 다시 부르면 재시도해요.
	 */
	@Bean(name = "reportExecutor", destroyMethod = "shutdown")
	ExecutorService reportExecutor() {
		return new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(16), runnable -> {
			Thread thread = new Thread(runnable, "report");
			thread.setDaemon(true);
			return thread;
		});
	}
}
