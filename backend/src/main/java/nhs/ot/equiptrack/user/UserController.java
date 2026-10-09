package nhs.ot.equiptrack.user;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import nhs.ot.equiptrack.auth.CurrentUser;

@RestController
@RequestMapping("/users")
@PreAuthorize("hasRole('MANAGER')")
class UserController {

	private final UserService service;

	UserController(UserService service) {
		this.service = service;
	}

	@GetMapping
	List<UserResponse> list() {
		return service.list();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	UserResponse create(@Valid @RequestBody UserRequests.Create request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	UserResponse update(@AuthenticationPrincipal CurrentUser me, @PathVariable long id,
			@Valid @RequestBody UserRequests.Update request) {
		return service.update(me.id(), id, request);
	}

	@PostMapping("/{id}/reset-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void resetPassword(@PathVariable long id, @Valid @RequestBody UserRequests.ResetPassword request) {
		service.resetPassword(id, request);
	}

}
