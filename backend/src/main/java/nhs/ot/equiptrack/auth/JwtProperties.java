package nhs.ot.equiptrack.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration ttl) {

	public JwtProperties {
		// HS256 needs a key of at least 256 bits; fail at startup rather than at first sign-in
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalStateException("JWT_SECRET must be set and at least 32 bytes long");
		}
	}

}
