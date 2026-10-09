package nhs.ot.equiptrack.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nhs.ot.equiptrack.common.ApiException;
import nhs.ot.equiptrack.user.AppUser;
import nhs.ot.equiptrack.user.UserRepository;

@Service
public class AuthService {

	static final String BAD_LOGIN = "Incorrect username or password";

	private final UserRepository users;
	private final PasswordEncoder encoder;
	private final TokenService tokens;
	private final LoginProperties props;
	private final Clock clock;
	private final String dummyHash;

	public AuthService(UserRepository users, PasswordEncoder encoder, TokenService tokens, LoginProperties props, Clock clock) {
		this.users = users;
		this.encoder = encoder;
		this.tokens = tokens;
		this.props = props;
		this.clock = clock;
		this.dummyHash = encoder.encode("timing-equaliser");
	}

	// Not @Transactional: the failed-attempt counter must be saved even though we then answer 401
	public LoginResponse login(LoginRequest request) {
		AppUser user = users.findByUsername(request.username().trim()).orElse(null);
		if (user == null) {
			encoder.matches(request.password(), dummyHash); // same work as a real check, so timing doesn't reveal usernames
			throw ApiException.unauthorized(BAD_LOGIN);
		}

		Instant now = clock.instant();
		if (user.isLocked(now)) {
			throw ApiException.unauthorized(lockedMessage(Duration.between(now, user.getLockedUntil())));
		}
		if (!encoder.matches(request.password(), user.getPasswordHash())) {
			users.recordFailedLogin(user.getId(), props.maxFailedAttempts(), now.plus(props.lockDuration()));
			boolean nowLocked = user.getFailedAttempts() + 1 >= props.maxFailedAttempts();
			throw ApiException.unauthorized(nowLocked ? lockedMessage(props.lockDuration()) : BAD_LOGIN);
		}
		if (!user.isActive()) {
			throw ApiException.unauthorized(JwtUserConverter.DEACTIVATED);
		}

		users.recordSuccessfulLogin(user.getId(), now);
		TokenService.IssuedToken token = tokens.issue(user);
		return new LoginResponse(token.value(), token.expiresAt(), UserSummary.of(CurrentUser.of(user)));
	}

	@Transactional
	public void changePassword(CurrentUser me, ChangePasswordRequest request) {
		AppUser user = users.findById(me.id()).orElseThrow();
		if (!encoder.matches(request.currentPassword(), user.getPasswordHash())) {
			// 400, not 401: a wrong current password must not sign the user out
			throw ApiException.badRequest("Your current password is incorrect");
		}
		if (request.newPassword().equals(request.currentPassword())) {
			throw ApiException.badRequest("Choose a password different from your current one");
		}
		user.changePassword(encoder.encode(request.newPassword()));
	}

	private static String lockedMessage(Duration remaining) {
		long minutes = Math.max(1, (remaining.toSeconds() + 59) / 60);
		return "Too many failed sign-in attempts. Try again in " + minutes + (minutes == 1 ? " minute." : " minutes.");
	}

}
