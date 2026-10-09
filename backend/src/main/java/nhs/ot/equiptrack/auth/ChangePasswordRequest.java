package nhs.ot.equiptrack.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
		@NotBlank(message = "Enter your current password") String currentPassword,
		@NotBlank(message = "Enter a new password")
		@Size(min = 8, max = 72, message = "The new password must be 8 to 72 characters") String newPassword) {
}
