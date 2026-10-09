package nhs.ot.equiptrack.auth;

import nhs.ot.equiptrack.user.AppUser;
import nhs.ot.equiptrack.user.Role;

// The signed-in user for one request. Controllers receive it with
// @AuthenticationPrincipal; it is never stored anywhere shared.
public record CurrentUser(long id, String username, String name, String team, Role role) {

	public static CurrentUser of(AppUser user) {
		return new CurrentUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getTeam(), user.getRole());
	}

	public boolean isManager() {
		return role == Role.MANAGER;
	}

}
