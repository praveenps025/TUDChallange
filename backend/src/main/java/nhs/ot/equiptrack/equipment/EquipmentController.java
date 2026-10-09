package nhs.ot.equiptrack.equipment;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

	// All filters optional; search matches ID, name, asset number, serial number or model
	@GetMapping
	EquipmentService.PageResponse<EquipmentResponse> search(
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String type,
			@RequestParam(required = false) String category,
			@RequestParam(required = false) String pool,
			@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.search(new EquipmentSearch(status, type, category, pool, search), page, size);
	}

	@GetMapping("/recent")
	List<EquipmentResponse> recent(@AuthenticationPrincipal CurrentUser user) {
		return service.recent(user);
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
