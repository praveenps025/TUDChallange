package nhs.ot.equiptrack.auth;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import nhs.ot.equiptrack.user.UserRepository;

// Resolves every request's token to its user. The user is re-read on each
// request so deactivation and role changes take effect immediately, even for
// tokens issued earlier.
@Component
class JwtUserConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	static final String DEACTIVATED = "This account has been deactivated. Ask a manager to reactivate it.";

	private final UserRepository users;

	JwtUserConverter(UserRepository users) {
		this.users = users;
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		var user = users.findByUsername(jwt.getSubject())
				.filter(u -> u.isActive())
				.orElseThrow(() -> new DisabledException(DEACTIVATED));
		return new UserAuthentication(CurrentUser.of(user), jwt);
	}

}
