package nhs.ot.equiptrack.equipment;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import nhs.ot.equiptrack.auth.CurrentUser;

@RestController
@RequestMapping("/equipment")
class EquipmentController {

	record ReturnRequest(@NotNull(message = "Say whether the item is clean and undamaged") Boolean clean) {
	}

	// pool is a funding pool code (GET /pools); location defaults to the OT store
	record RegisterRequest(String type, String pool, String location,
			@NotNull(message = "Enter the item's details") @Valid EquipmentDetails details) {
	}

	record UpdateRequest(String pool, @NotNull(message = "Enter the item's details") @Valid EquipmentDetails details) {
	}

	private final EquipmentService service;
	private final EquipmentRegistry registry;
	private final EquipmentFinder finder;

	EquipmentController(EquipmentService service, EquipmentRegistry registry, EquipmentFinder finder) {
		this.service = service;
		this.registry = registry;
		this.finder = finder;
	}

	@PostMapping
	@PreAuthorize("hasRole('MANAGER')")
	@ResponseStatus(HttpStatus.CREATED)
	EquipmentRegistry.DetailsResponse register(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody RegisterRequest request) {
		return registry.register(user, request.type(), request.pool(), request.location(), request.details());
	}

	@GetMapping("/{id}/details")
	EquipmentRegistry.DetailsResponse details(@PathVariable String id) {
		return registry.details(id);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('MANAGER')")
	EquipmentRegistry.DetailsResponse update(@AuthenticationPrincipal CurrentUser user, @PathVariable String id,
			@Valid @RequestBody UpdateRequest request) {
		return registry.update(user, id, request.pool(), request.details());
	}

	// Called when a label is printed or reprinted, so the activity log records it
	@PostMapping("/{id}/label-printed")
	@PreAuthorize("hasRole('MANAGER')")
	EquipmentRegistry.DetailsResponse labelPrinted(@AuthenticationPrincipal CurrentUser user, @PathVariable String id) {
		return registry.labelPrinted(user, id);
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

	// "Find equipment": what can be taken right now. weight is a patient weight
	// category; items rated for it or a heavier band are listed.
	@GetMapping("/available")
	EquipmentFinder.Availability available(
			@RequestParam(required = false) String category,
			@RequestParam(required = false) String type,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) String weight) {
		return finder.find(category, type, search, weight);
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
