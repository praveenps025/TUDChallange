package nhs.ot.equiptrack.insights;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Managers only: staff don't see the analytics
@RestController
@RequestMapping("/insights")
@PreAuthorize("hasRole('MANAGER')")
class InsightsController {

	private final InsightsService service;

	InsightsController(InsightsService service) {
		this.service = service;
	}

	// period: this-month (default), last-month or this-year
	@GetMapping
	InsightsService.Overview overview(@RequestParam(required = false) String period) {
		return service.overview(period);
	}

}
