package com.dearus.upload;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessengerConfig {

	/** 메신저 요약을 뒤에서 하는 작업 줄이에요. 동시에 2건만 돌려서 AI 호출이 한꺼번에 몰리지 않게 해요. */
	@Bean(name = "messengerExecutor", destroyMethod = "shutdown")
	ExecutorService messengerExecutor() {
		return Executors.newFixedThreadPool(2, runnable -> {
			Thread thread = new Thread(runnable, "messenger-summary");
			thread.setDaemon(true);
			return thread;
		});
	}
}
