package nhs.ot.equiptrack.homeloan;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import nhs.ot.equiptrack.auth.CurrentUser;

@RestController
@RequestMapping("/home-loans")
class HomeLoanController {

	// mrn: last four digits or the full MRN; dueOn (yyyy-mm-dd) optional per item
	record CreateRequest(
			@NotBlank(message = "Enter the patient's MRN") String mrn,
			@NotEmpty(message = "Choose at least one item to lend") List<@Valid ItemRequest> items,
			@Size(max = 200, message = "Keep the note under 200 characters") String note) {
	}

	record ItemRequest(@NotBlank(message = "Choose the item to lend") String equipmentId, java.time.LocalDate dueOn) {
	}

	record ReasonRequest(
			@NotBlank(message = "A reason is required") @Size(max = 500, message = "Keep the reason under 500 characters") String reason) {
	}

	private final HomeLoanService service;

	HomeLoanController(HomeLoanService service) {
		this.service = service;
	}

	// Staff: waits for a manager. Manager: approved at once.
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	HomeLoanInfo create(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody CreateRequest request) {
		return service.request(user, request.mrn(),
				request.items().stream().map(i -> new HomeLoanService.ItemInput(i.equipmentId(), i.dueOn())).toList(), request.note());
	}

	@GetMapping("/pending")
	@PreAuthorize("hasRole('MANAGER')")
	List<HomeLoanInfo> pending() {
		return service.pending();
	}

	@PostMapping("/{id}/approve")
	@PreAuthorize("hasRole('MANAGER')")
	HomeLoanInfo approve(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, @Valid @RequestBody ReasonRequest request) {
		return service.decide(user, id, true, request.reason());
	}

	@PostMapping("/{id}/reject")
	@PreAuthorize("hasRole('MANAGER')")
	HomeLoanInfo reject(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, @Valid @RequestBody ReasonRequest request) {
		return service.decide(user, id, false, request.reason());
	}

}
