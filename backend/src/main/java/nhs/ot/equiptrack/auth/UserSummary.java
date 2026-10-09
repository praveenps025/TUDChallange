package nhs.ot.equiptrack.auth;

import nhs.ot.equiptrack.user.Role;

public record UserSummary(String name, String team, Role role) {

	public static UserSummary of(CurrentUser user) {
		return new UserSummary(user.name(), user.team(), user.role());
	}

}
