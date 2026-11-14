package com.dearus.common.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Anthropic API로 Claude Haiku 5.5(`claude-haiku-5-5`)를 불러요.
 * Haiku 5.5는 temperature 같은 샘플링 값과 마지막 assistant 메시지(prefill)를 받지 않고(400),
 * 생각(thinking)이 기본으로 켜져 있어서 비용과 속도는 effort로 조절해요.
 * 키가 없어도 서버는 켜지고, 이 클래스를 부르는 순간에만 AI_UNAVAILABLE이 돼요.
 */
@Component
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "anthropic", matchIfMissing = true)
public class AnthropicAiClient implements AiClient {

	private final AiProperties properties;
	private volatile AnthropicClient client;

	public AnthropicAiClient(AiProperties properties) {
		this.properties = properties;
	}

	@Override
	public AiResult complete(AiRequest request) {
		Message response;
		try {
			response = client().messages().create(params(request));
		} catch (AnthropicException e) {
			throw unavailable(e);
		}

		String stopReason = response.stopReason().map(r -> r.asString()).orElse("");
		if (stopReason.equals("refusal")) {
			throw new AiException(AiException.Reason.REFUSED, "AI가 요청을 거절했어요");
		}
		if (stopReason.equals("max_tokens")) {
			throw new AiException(AiException.Reason.BAD_RESPONSE, "AI의 답이 중간에 잘렸어요");
		}
		// 생각 블록이 먼저 올 수 있어서 위치가 아니라 종류(text)로 골라요
		String text = response.content().stream()
			.flatMap(block -> block.text().stream())
			.map(textBlock -> textBlock.text())
			.collect(Collectors.joining());
		if (text.isBlank()) {
			throw new AiException(AiException.Reason.BAD_RESPONSE, "AI의 답이 비어 있어요");
		}
		return new AiResult(text, response.usage().inputTokens(), response.usage().outputTokens());
	}

	/**
	 * 답이 만들어지는 대로 글자 조각을 onText로 넘겨요(첫 글자까지 보통 1초 안). 거절(refusal)이면 AI_REFUSED예요.
	 * 답이 max_tokens에서 끊겼어도 글자가 있으면 그대로 돌려줘요(채팅 답은 JSON처럼 완전해야 하는 건 아니라서요).
	 */
	@Override
	public AiResult stream(AiRequest request, Consumer<String> onText) {
		StringBuilder text = new StringBuilder();
		String[] stopReason = {""};
		long[] tokens = {0, 0};
		try (StreamResponse<RawMessageStreamEvent> stream = client().messages().createStreaming(params(request))) {
			stream.stream().forEach(event -> {
				event.messageStart().ifPresent(start -> tokens[0] = start.message().usage().inputTokens());
				event.contentBlockDelta().ifPresent(delta -> delta.delta().text().ifPresent(piece -> {
					text.append(piece.text());
					onText.accept(piece.text());
				}));
				event.messageDelta().ifPresent(delta -> {
					delta.delta().stopReason().ifPresent(reason -> stopReason[0] = reason.asString());
					tokens[1] = delta.usage().outputTokens();
				});
			});
		} catch (AnthropicException e) {
			throw unavailable(e);
		}
		if (stopReason[0].equals("refusal")) {
			throw new AiException(AiException.Reason.REFUSED, "AI가 요청을 거절했어요");
		}
		if (text.toString().isBlank()) {
			throw new AiException(AiException.Reason.BAD_RESPONSE, "AI의 답이 비어 있어요");
		}
		return new AiResult(text.toString(), tokens[0], tokens[1]);
	}

	private MessageCreateParams params(AiRequest request) {
		MessageCreateParams.Builder params = MessageCreateParams.builder()
			.model(properties.model())
			.maxTokens((long) request.maxTokens())
			.outputConfig(OutputConfig.builder().effort(toEffort(request.effort())).build());
		if (request.system() != null && !request.system().isBlank()) {
			params.system(request.system());
		}
		for (AiMessage message : request.messages()) {
			if (message.role() == AiMessage.Role.USER) {
				params.addUserMessage(message.text());
			} else {
				params.addAssistantMessage(message.text());
			}
		}
		return params.build();
	}

	private static AiException unavailable(AnthropicException e) {
		// 오류 메시지에 요청 내용이 섞여 있을 수 있어서 글자는 남기지 않고 종류와 HTTP 상태만 남겨요
		// (예: 400 BadRequestException은 크레딧 부족일 수 있어요. 자세한 이유는 Anthropic Console에서 봐요)
		String status = e instanceof AnthropicServiceException service ? " (HTTP " + service.statusCode() + ")" : "";
		return new AiException(AiException.Reason.UNAVAILABLE, "AI를 부르지 못했어요: " + e.getClass().getSimpleName() + status, e);
	}

	private AnthropicClient client() {
		if (properties.apiKey() == null || properties.apiKey().isBlank()) {
			throw new AiException(AiException.Reason.UNAVAILABLE, "ANTHROPIC_API_KEY가 설정되지 않았어요");
		}
		AnthropicClient existing = client;
		if (existing == null) {
			synchronized (this) {
				if (client == null) {
					client = AnthropicOkHttpClient.builder()
						.apiKey(properties.apiKey())
						.timeout(Duration.ofSeconds(properties.timeoutSeconds()))
						.build();
				}
				existing = client;
			}
		}
		return existing;
	}

	private static OutputConfig.Effort toEffort(AiEffort effort) {
		return switch (effort) {
			case LOW -> OutputConfig.Effort.LOW;
			case MEDIUM -> OutputConfig.Effort.MEDIUM;
			case HIGH -> OutputConfig.Effort.HIGH;
		};
	}
}
