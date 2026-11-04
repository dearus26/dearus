package com.dearus.auth;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml의 app.auth.
 * - devLoginEnabled: 환경변수 DEV_LOGIN_ENABLED예요 (기본 꺼짐).
 * - kakao.appId: 카카오 개발자 콘솔의 앱 ID(숫자)예요. 환경변수 KAKAO_APP_ID. 비어 있으면 카카오 로그인은 501이에요.
 * - google.clientIds: 구글 ID token의 대상(aud)으로 받아들일 클라이언트 ID 목록이에요. 환경변수 GOOGLE_CLIENT_IDS(쉼표로 구분).
 *   앱의 구글 로그인이 웹 · 안드로이드 · iOS 중 어느 ID로 토큰을 만드는지에 맞춰 넣어요. 비어 있으면 구글 로그인은 501이에요.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(boolean devLoginEnabled, Kakao kakao, Google google) {

	public record Kakao(String appId) {

		public Kakao {
			appId = appId == null ? "" : appId.strip();
		}
	}

	public record Google(List<String> clientIds) {

		public Google {
			clientIds = clientIds == null ? List.of() : clientIds.stream().map(String::strip).filter(id -> !id.isEmpty()).toList();
		}
	}

	public AuthProperties {
		kakao = kakao == null ? new Kakao("") : kakao;
		google = google == null ? new Google(List.of()) : google;
	}
}
