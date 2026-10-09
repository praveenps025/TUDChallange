package nhs.ot.equiptrack.auth;

import java.time.Clock;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import nhs.ot.equiptrack.user.AppUser;

// Issues signed, stateless tokens. Nothing is stored server-side, so any
// number of users and devices can be signed in at once.
@Service
public class TokenService {

	static final String ISSUER = "equiptrack";

	private final JwtEncoder encoder;
	private final JwtProperties props;
	private final Clock clock;

	public TokenService(JwtEncoder encoder, JwtProperties props, Clock clock) {
		this.encoder = encoder;
		this.props = props;
		this.clock = clock;
	}

	public record IssuedToken(String value, Instant expiresAt) {
	}

	public IssuedToken issue(AppUser user) {
		Instant now = clock.instant();
		Instant expiresAt = now.plus(props.ttl());
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(ISSUER)
				.subject(user.getUsername())
				.issuedAt(now)
				.expiresAt(expiresAt)
				.claim("role", user.getRole().name())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return new IssuedToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), expiresAt);
	}

}
