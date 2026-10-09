package nhs.ot.equiptrack.insights;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/insights")
class InsightsController {

	private final InsightsService service;

	InsightsController(InsightsService service) {
		this.service = service;
	}

	@GetMapping
	InsightsService.Overview overview() {
		return service.overview();
	}

}
