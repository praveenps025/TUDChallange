package nhs.ot.equiptrack.auth;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

// Authentication for one request: the principal is the CurrentUser loaded from the database
class UserAuthentication extends AbstractAuthenticationToken {

	private final CurrentUser user;
	private final Jwt jwt;

	UserAuthentication(CurrentUser user, Jwt jwt) {
		super(List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
		this.user = user;
		this.jwt = jwt;
		setAuthenticated(true);
	}

	@Override
	public CurrentUser getPrincipal() {
		return user;
	}

	@Override
	public Jwt getCredentials() {
		return jwt;
	}

	@Override
	public String getName() {
		return user.username();
	}

}
