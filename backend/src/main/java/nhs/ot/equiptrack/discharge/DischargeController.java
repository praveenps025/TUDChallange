package nhs.ot.equiptrack.discharge;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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

	record OverrideRequest(
			@NotBlank(message = "A reason is required") @Size(max = 500, message = "Keep the reason under 500 characters") String reason) {
	}

	private final DischargeService service;

	DischargeController(DischargeService service) {
		this.service = service;
	}

	@GetMapping("/{mrn}")
	DischargeService.PatientResponse find(@PathVariable String mrn) {
		return service.find(mrn);
	}

	@PostMapping("/{mrn}/override")
	DischargeService.PatientResponse override(@AuthenticationPrincipal CurrentUser user, @PathVariable String mrn,
			@Valid @RequestBody OverrideRequest request) {
		return service.requestOverride(user, mrn, request.reason());
	}

}
