package nhs.ot.equiptrack.waitlist;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import nhs.ot.equiptrack.auth.CurrentUser;
import nhs.ot.equiptrack.equipment.EquipmentResponse;

@RestController
@RequestMapping("/waitlist")
class WaitlistController {

	// dueOn (yyyy-mm-dd) is optional; the equipment type's loan length is used otherwise
	record AllocateRequest(@NotBlank(message = "Choose the item to allocate") String equipmentId, LocalDate dueOn) {
	}

	private final WaitlistService service;

	WaitlistController(WaitlistService service) {
		this.service = service;
	}

	@GetMapping
	List<WaitlistService.Entry> list() {
		return service.list();
	}

	@PostMapping("/{id}/allocate")
	EquipmentResponse allocate(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@Valid @RequestBody AllocateRequest request) {
		return service.allocate(user, id, request.equipmentId(), request.dueOn());
	}

}
