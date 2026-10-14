package com.dearus.auth;

import com.dearus.common.Kst;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

	public record IssuedToken(String value, OffsetDateTime expiresAt) {
	}

	private final JwtEncoder encoder;
	private final JwtProperties props;

	public JwtService(JwtEncoder encoder, JwtProperties props) {
		this.encoder = encoder;
		this.props = props;
	}

	/** 사용자 ID를 담은 토큰을 만들어요. 만료되면 앱이 401을 받고 다시 로그인해요. */
	public IssuedToken issue(UUID userId) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(props.ttlDays(), ChronoUnit.DAYS);
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer("dearus")
			.subject(userId.toString())
			.issuedAt(now)
			.expiresAt(expiresAt)
			.build();
		String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();
		return new IssuedToken(token, expiresAt.atZone(Kst.ZONE).toOffsetDateTime());
	}
}
