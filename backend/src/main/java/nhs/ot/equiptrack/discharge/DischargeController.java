package nhs.ot.equiptrack.discharge;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import nhs.ot.equiptrack.auth.CurrentUser;

@RestController
@RequestMapping("/discharge")
class DischargeController {

	record ReasonRequest(
			@NotBlank(message = "A reason is required") @Size(max = 500, message = "Keep the reason under 500 characters") String reason) {
	}

	private final DischargeService service;

	DischargeController(DischargeService service) {
		this.service = service;
	}

	@GetMapping("/overrides")
	@PreAuthorize("hasRole('MANAGER')")
	List<DischargeService.PendingOverride> pending() {
		return service.pending();
	}

	@PostMapping("/overrides/{id}/approve")
	@PreAuthorize("hasRole('MANAGER')")
	DischargeService.PatientResponse approve(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@Valid @RequestBody ReasonRequest request) {
		return service.decide(user, id, true, request.reason());
	}

	@PostMapping("/overrides/{id}/reject")
	@PreAuthorize("hasRole('MANAGER')")
	DischargeService.PatientResponse reject(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@Valid @RequestBody ReasonRequest request) {
		return service.decide(user, id, false, request.reason());
	}

	@GetMapping("/{mrn}")
	DischargeService.PatientResponse find(@PathVariable String mrn) {
		return service.find(mrn);
	}

	@PostMapping("/{mrn}/override")
	DischargeService.PatientResponse override(@AuthenticationPrincipal CurrentUser user, @PathVariable String mrn,
			@Valid @RequestBody ReasonRequest request) {
		return service.requestOverride(user, mrn, request.reason());
	}

}
