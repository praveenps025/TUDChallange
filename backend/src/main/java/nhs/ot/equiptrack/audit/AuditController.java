package nhs.ot.equiptrack.audit;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import nhs.ot.equiptrack.auth.CurrentUser;

@RestController
@RequestMapping("/audit")
class AuditController {

	record StartRequest(@NotEmpty(message = "Choose at least one location") List<@NotBlank String> locations,
			@Size(max = 100, message = "Area name is too long") String area) {
	}

	record ScanRequest(@NotBlank(message = "Scan or enter an equipment ID") String equipmentId) {
	}

	private final AuditService service;

	AuditController(AuditService service) {
		this.service = service;
	}

	@GetMapping
	AuditService.WalkResponse current() {
		return service.current();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	AuditService.WalkResponse start(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody StartRequest request) {
		return service.start(user, request.locations(), request.area());
	}

	@PostMapping("/scan")
	AuditService.WalkResponse scan(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody ScanRequest request) {
		return service.scan(user, request.equipmentId());
	}

	@PostMapping("/finish")
	AuditService.FinishResponse finish(@AuthenticationPrincipal CurrentUser user) {
		return service.finish(user);
	}

}
