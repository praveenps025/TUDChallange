package nhs.ot.equiptrack.user;

import java.time.Instant;

public record UserResponse(long id, String username, String name, String team, Role role, boolean active,
		boolean locked, Instant lastLoginAt) {

	static UserResponse of(AppUser user, Instant now) {
		return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getTeam(), user.getRole(),
				user.isActive(), user.isLocked(now), user.getLastLoginAt());
	}

}
