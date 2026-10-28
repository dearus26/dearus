package com.dearus.upload;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessengerConfig {

	/** 메신저 요약을 뒤에서 하는 작업 줄이에요. 업로드를 동시에 2건만 처리해요. */
	@Bean(name = "messengerExecutor", destroyMethod = "shutdown")
	ExecutorService messengerExecutor() {
		return daemonPool(2, "messenger-summary");
	}

	/** 업로드 안에서 주마다 AI를 부르는 줄이에요. 동시에 4번만 불러서 AI 호출이 한꺼번에 몰리지 않게 해요(전체 상한). */
	@Bean(name = "summaryCallExecutor", destroyMethod = "shutdown")
	ExecutorService summaryCallExecutor() {
		return daemonPool(4, "summary-call");
	}

	private static ExecutorService daemonPool(int size, String name) {
		return Executors.newFixedThreadPool(size, runnable -> {
			Thread thread = new Thread(runnable, name);
			thread.setDaemon(true);
			return thread;
		});
	}
}
