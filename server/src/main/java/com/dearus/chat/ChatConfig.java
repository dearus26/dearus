package com.dearus.chat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfig {

	/** 대화 세션의 시점 분석을 뒤에서 하는 작업 줄이에요. 동시에 2건만 처리해서 AI 호출이 한꺼번에 몰리지 않게 해요. */
	@Bean(name = "chatPrepareExecutor", destroyMethod = "shutdown")
	ExecutorService chatPrepareExecutor() {
		return Executors.newFixedThreadPool(2, runnable -> {
			Thread thread = new Thread(runnable, "chat-prepare");
			thread.setDaemon(true);
			return thread;
		});
	}
}
