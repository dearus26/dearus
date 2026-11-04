package com.dearus.auth;

import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 카카오 access token을 카카오 서버에 물어서 확인해요(2026.11.4 결정, 이메일은 받지 않아요).
 * ① GET /v1/user/access_token_info로 토큰이 **우리 앱**(app_id)에서 발급된 것인지 확인해요. 다른 카카오 앱의 토큰으로 로그인하는 것을 막아요.
 * ② GET /v2/user/me로 사용자 id와 닉네임(kakao_account.profile.nickname, 동의하지 않았으면 없음)을 읽어요.
 * 카카오가 400 · 401 · 403이면 토큰 문제(401)이고, 그 밖의 오류와 연결 실패는 제공자 장애(503)예요.
 */
@Component
public class KakaoTokenVerifier implements SocialTokenVerifier {

	private static final Logger log = LoggerFactory.getLogger(KakaoTokenVerifier.class);

	static final String BASE_URL = "https://kapi.kakao.com";
	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private final RestClient rest;
	private final String appId;

	@Autowired
	public KakaoTokenVerifier(AuthProperties props) {
		this(defaultRestClient(), props.kakao().appId());
	}

	/** 시험에서 가짜 서버를 끼울 수 있게 열어 둔 생성자예요. */
	KakaoTokenVerifier(RestClient rest, String appId) {
		this.rest = rest;
		this.appId = appId == null ? "" : appId.strip();
		if (!this.appId.isEmpty() && !isConfigured()) {
			log.warn("KAKAO_APP_ID는 카카오 개발자 콘솔의 숫자 앱 ID여야 해요. 이 값으로는 카카오 로그인을 켜지 않아요.");
		}
	}

	/** 연결 3초 · 응답 5초 안에 못 받으면 제공자 장애로 봐요. */
	static RestClient defaultRestClient() {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
		factory.setReadTimeout(Duration.ofSeconds(5));
		return RestClient.builder().baseUrl(BASE_URL).requestFactory(factory).build();
	}

	@Override
	public String provider() {
		return "kakao";
	}

	@Override
	public boolean isConfigured() {
		return appId.matches("\\d{1,18}");
	}

	@Override
	public VerifiedIdentity verify(String token) {
		// Authorization 헤더에 그대로 들어가서, 보이는 ASCII 글자가 아니면 카카오에 보내지 않고 토큰 문제로 봐요(헤더 주입 방지)
		if (!token.matches("[\\x21-\\x7E]+")) {
			throw new AuthProviderException(AuthProviderException.Reason.INVALID, "카카오 토큰 형식이 올바르지 않아요");
		}

		JsonNode info = get("/v1/user/access_token_info", token);
		JsonNode tokenAppId = info.get("app_id");
		if (tokenAppId == null || !tokenAppId.isNumber() || tokenAppId.asLong() != Long.parseLong(appId)) {
			throw new AuthProviderException(AuthProviderException.Reason.INVALID, "이 앱에서 발급된 카카오 토큰이 아니에요");
		}

		JsonNode me = get("/v2/user/me", token);
		JsonNode id = me.get("id");
		if (id == null || !id.isNumber()) {
			log.error("카카오 사용자 정보 응답에 id가 없어요");
			throw new AuthProviderException(AuthProviderException.Reason.UNAVAILABLE, "카카오 응답을 읽지 못했어요");
		}
		JsonNode nickname = me.path("kakao_account").path("profile").path("nickname");
		return new VerifiedIdentity(String.valueOf(id.asLong()), nickname.isString() ? nickname.asString() : null);
	}

	private JsonNode get(String path, String token) {
		try {
			String body = rest.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).retrieve().body(String.class);
			return MAPPER.readTree(body == null ? "" : body);
		} catch (RestClientResponseException e) {
			int status = e.getStatusCode().value();
			if (status == 400 || status == 401 || status == 403) {
				log.warn("카카오가 토큰을 받아주지 않았어요 path={} status={}", path, status);
				throw new AuthProviderException(AuthProviderException.Reason.INVALID, "카카오 토큰이 올바르지 않아요", e);
			}
			log.error("카카오 서버 오류 path={} status={}", path, status);
			throw new AuthProviderException(AuthProviderException.Reason.UNAVAILABLE, "카카오 서버가 응답하지 못해요", e);
		} catch (RestClientException e) {
			log.error("카카오에 연결하지 못했어요 path={} reason={}", path, e.getClass().getSimpleName());
			throw new AuthProviderException(AuthProviderException.Reason.UNAVAILABLE, "카카오에 연결하지 못했어요", e);
		} catch (JacksonException e) {
			log.error("카카오 응답을 읽지 못했어요 path={}", path);
			throw new AuthProviderException(AuthProviderException.Reason.UNAVAILABLE, "카카오 응답을 읽지 못했어요", e);
		}
	}
}
