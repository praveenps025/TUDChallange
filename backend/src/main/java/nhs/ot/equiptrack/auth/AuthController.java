package nhs.ot.equiptrack.auth;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Sign-out needs no endpoint: tokens are stateless, so the client simply
// discards its token. Other devices on the same account stay signed in.
@RestController
@RequestMapping("/auth")
class AuthController {

	private final AuthService auth;

	AuthController(AuthService auth) {
		this.auth = auth;
	}

	@PostMapping("/login")
	LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return auth.login(request);
	}

	@GetMapping("/me")
	UserSummary me(@AuthenticationPrincipal CurrentUser me) {
		return UserSummary.of(me);
	}

	@PostMapping("/change-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void changePassword(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody ChangePasswordRequest request) {
		auth.changePassword(me, request);
	}

}
