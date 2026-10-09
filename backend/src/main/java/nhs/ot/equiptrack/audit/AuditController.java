package nhs.ot.equiptrack.audit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit")
class AuditController {

	private final AuditService service;

	AuditController(AuditService service) {
		this.service = service;
	}

	@GetMapping
	AuditService.WalkResponse current() {
		return service.current();
	}

}
