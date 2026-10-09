package nhs.ot.equiptrack.user;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nhs.ot.equiptrack.common.ApiException;

@Service
public class UserService {

	private final UserRepository users;
	private final PasswordEncoder encoder;
	private final Clock clock;

	public UserService(UserRepository users, PasswordEncoder encoder, Clock clock) {
		this.users = users;
		this.encoder = encoder;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<UserResponse> list() {
		Instant now = clock.instant();
		return users.findAllByOrderByDisplayNameAsc().stream().map(u -> UserResponse.of(u, now)).toList();
	}

	@Transactional
	public UserResponse create(UserRequests.Create request) {
		String username = request.username().trim();
		if (users.findByUsername(username).isPresent()) {
			throw ApiException.conflict("The username " + username + " is already taken");
		}
		AppUser user = new AppUser(username, encoder.encode(request.password()), request.name().trim(),
				request.team().trim(), request.role());
		return UserResponse.of(users.save(user), clock.instant());
	}

	@Transactional
	public UserResponse update(long actingUserId, long id, UserRequests.Update request) {
		AppUser user = find(id);
		// Stops a manager locking everyone (including themselves) out of user management
		if (id == actingUserId && (!request.active() || request.role() != Role.MANAGER)) {
			throw ApiException.conflict("You can't deactivate your own account or remove your own manager role");
		}
		user.updateProfile(request.name().trim(), request.team().trim(), request.role(), request.active());
		return UserResponse.of(user, clock.instant());
	}

	@Transactional
	public void resetPassword(long id, UserRequests.ResetPassword request) {
		find(id).resetPassword(encoder.encode(request.password()));
	}

	private AppUser find(long id) {
		return users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
	}

}
