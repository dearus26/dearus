package com.dearus.common.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import java.time.Duration;
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

		Message response;
		try {
			response = client().messages().create(params.build());
		} catch (AnthropicException e) {
			// 오류 메시지에 요청 내용이 섞여 있을 수 있어서 글자는 남기지 않고 종류와 HTTP 상태만 남겨요
			// (예: 400 BadRequestException은 크레딧 부족일 수 있어요. 자세한 이유는 Anthropic Console에서 봐요)
			String status = e instanceof AnthropicServiceException service ? " (HTTP " + service.statusCode() + ")" : "";
			throw new AiException(AiException.Reason.UNAVAILABLE, "AI를 부르지 못했어요: " + e.getClass().getSimpleName() + status, e);
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
