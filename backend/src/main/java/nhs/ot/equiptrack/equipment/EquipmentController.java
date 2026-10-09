package nhs.ot.equiptrack.equipment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import nhs.ot.equiptrack.auth.CurrentUser;

@RestController
@RequestMapping("/equipment")
class EquipmentController {

	record ReturnRequest(@NotNull(message = "Say whether the item is clean and undamaged") Boolean clean) {
	}

	private final EquipmentService service;

	EquipmentController(EquipmentService service) {
		this.service = service;
	}

	@GetMapping("/{id}")
	EquipmentResponse get(@PathVariable String id) {
		return service.get(id);
	}

	@PostMapping("/{id}/return")
	EquipmentResponse returnItem(@AuthenticationPrincipal CurrentUser user, @PathVariable String id,
			@Valid @RequestBody ReturnRequest request) {
		return service.returnItem(user, id, request.clean());
	}

	@PostMapping("/{id}/inspect")
	EquipmentResponse inspect(@AuthenticationPrincipal CurrentUser user, @PathVariable String id) {
		return service.inspect(user, id);
	}

}
