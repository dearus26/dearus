package com.dearus.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** 로그인 토큰(JWT)을 만들고 검증하는 도구예요. HS256, 서명 키는 JWT_SECRET이에요. */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, AuthProperties.class})
public class JwtConfig {

	private static SecretKey secretKey(JwtProperties props) {
		byte[] bytes = props.secret() == null ? new byte[0] : props.secret().getBytes(StandardCharsets.UTF_8);
		if (bytes.length < 32) {
			throw new IllegalStateException("JWT_SECRET은 32자 이상이어야 해요. server/.env에 값을 넣어 주세요.");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(JwtProperties props) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(props)));
	}

	@Bean
	JwtDecoder jwtDecoder(JwtProperties props) {
		return NimbusJwtDecoder.withSecretKey(secretKey(props)).macAlgorithm(MacAlgorithm.HS256).build();
	}
}
