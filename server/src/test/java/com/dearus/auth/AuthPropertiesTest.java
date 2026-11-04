package com.dearus.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** application.yml의 환경변수 값(KAKAO_APP_ID, GOOGLE_CLIENT_IDS)이 설정으로 잘 읽히는지 확인해요. */
class AuthPropertiesTest {

	private static AuthProperties bind(Map<String, String> values) {
		return new Binder(new MapConfigurationPropertySource(values)).bind("app.auth", AuthProperties.class).orElseGet(() -> new AuthProperties(false, null, null));
	}

	@Test
	void 값이_비어_있으면_카카오와_구글은_설정되지_않은_거예요() {
		AuthProperties props = bind(Map.of("app.auth.dev-login-enabled", "false", "app.auth.kakao.app-id", "", "app.auth.google.client-ids", ""));

		assertThat(props.kakao().appId()).isEmpty();
		assertThat(props.google().clientIds()).isEmpty();
	}

	@Test
	void 아예_없어도_빈_설정이에요() {
		AuthProperties props = bind(Map.of());

		assertThat(props.devLoginEnabled()).isFalse();
		assertThat(props.kakao().appId()).isEmpty();
		assertThat(props.google().clientIds()).isEmpty();
	}

	@Test
	void 구글_클라이언트_ID는_쉼표로_여러_개를_넣고_공백과_빈_칸은_버려요() {
		AuthProperties props = bind(Map.of("app.auth.google.client-ids", " web.apps.googleusercontent.com, ios.apps.googleusercontent.com ,,", "app.auth.kakao.app-id", " 123456 ",
			"app.auth.dev-login-enabled", "true"));

		assertThat(props.google().clientIds()).containsExactly("web.apps.googleusercontent.com", "ios.apps.googleusercontent.com");
		assertThat(props.kakao().appId()).isEqualTo("123456");
		assertThat(props.devLoginEnabled()).isTrue();
	}
}
