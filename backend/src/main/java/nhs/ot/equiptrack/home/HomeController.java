package nhs.ot.equiptrack.home;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import nhs.ot.equiptrack.auth.CurrentUser;

@RestController
@RequestMapping("/home")
class HomeController {

	private final HomeService service;

	HomeController(HomeService service) {
		this.service = service;
	}

	@GetMapping
	HomeService.HomeResponse home(@AuthenticationPrincipal CurrentUser user) {
		return service.home(user);
	}

}
