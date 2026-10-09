package nhs.ot.equiptrack.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Request bodies for the user management endpoints
public final class UserRequests {

	private UserRequests() {
	}

	public record Create(
			@NotBlank(message = "Enter a username")
			@Pattern(regexp = "^[A-Za-z0-9._-]{3,60}$", message = "Usernames are 3 to 60 letters, numbers, dots, dashes or underscores")
			String username,
			@NotBlank(message = "Enter a name") @Size(max = 100, message = "Name is too long") String name,
			@NotBlank(message = "Enter a team") @Size(max = 100, message = "Team is too long") String team,
			@NotNull(message = "Choose a role") Role role,
			@NotBlank(message = "Enter a password")
			@Size(min = 8, max = 72, message = "Passwords must be 8 to 72 characters") String password) {
	}

	public record Update(
			@NotBlank(message = "Enter a name") @Size(max = 100, message = "Name is too long") String name,
			@NotBlank(message = "Enter a team") @Size(max = 100, message = "Team is too long") String team,
			@NotNull(message = "Choose a role") Role role,
			@NotNull(message = "Say whether the account is active") Boolean active) {
	}

	public record ResetPassword(
			@NotBlank(message = "Enter a password")
			@Size(min = 8, max = 72, message = "Passwords must be 8 to 72 characters") String password) {
	}

}
