package com.dearus.chat;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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

	/**
	 * 대화 답변을 SSE로 흘려보내는 작업 줄이에요. 답변 하나가 1~2초 걸려서 동시에 16개까지 처리하고, 대기는 32개까지만 받아요.
	 * 넘치면 503 SERVER_BUSY예요(AI 호출이 한꺼번에 몰리지 않게 하는 전체 상한이기도 해요).
	 */
	@Bean(name = "chatStreamExecutor", destroyMethod = "shutdown")
	ExecutorService chatStreamExecutor() {
		return new ThreadPoolExecutor(16, 16, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(32), runnable -> {
			Thread thread = new Thread(runnable, "chat-stream");
			thread.setDaemon(true);
			return thread;
		});
	}
}
