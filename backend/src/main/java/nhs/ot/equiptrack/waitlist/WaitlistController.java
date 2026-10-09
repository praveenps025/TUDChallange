package nhs.ot.equiptrack.waitlist;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import nhs.ot.equiptrack.auth.CurrentUser;
import nhs.ot.equiptrack.equipment.EquipmentResponse;

@RestController
class WaitlistController {

	// dueOn (yyyy-mm-dd) is optional; the equipment type's loan length is used otherwise
	record AllocateRequest(@NotBlank(message = "Choose the item to allocate") String equipmentId, LocalDate dueOn) {
	}

	record ReturnAndAllocateRequest(@NotNull(message = "Choose the request to allocate to") Long requestId, LocalDate dueOn) {
	}

	// priority: urgent, soon or routine
	record CreateRequest(
			@NotBlank(message = "Choose the item needed") String type,
			@NotBlank(message = "Choose a ward") String ward,
			@NotBlank(message = "Enter the patient's MRN") @Size(max = 30, message = "MRN is too long") String mrn,
			@NotBlank(message = "Choose a priority") String priority,
			@Size(max = 10, message = "Bed is too long") String bed,
			@Size(max = 200, message = "Keep the note under 200 characters") String note) {
	}

	private final WaitlistService service;

	WaitlistController(WaitlistService service) {
		this.service = service;
	}

	@GetMapping("/waitlist")
	List<WaitlistService.Entry> list() {
		return service.list();
	}

	@PostMapping("/waitlist")
	@ResponseStatus(HttpStatus.CREATED)
	WaitlistService.Entry create(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody CreateRequest request) {
		return service.create(user, new WaitlistService.NewRequest(request.ward(), request.type(), request.mrn(), request.bed(),
				request.priority(), request.note()));
	}

	@DeleteMapping("/waitlist/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
		service.cancel(user, id);
	}

	@PostMapping("/waitlist/{id}/allocate")
	EquipmentResponse allocate(@AuthenticationPrincipal CurrentUser user, @PathVariable long id,
			@Valid @RequestBody AllocateRequest request) {
		return service.allocate(user, id, request.equipmentId(), request.dueOn());
	}

	// Lives here rather than in EquipmentController because it serves a waitlist request
	@PostMapping("/equipment/{id}/return-and-allocate")
	EquipmentResponse returnAndAllocate(@AuthenticationPrincipal CurrentUser user, @PathVariable String id,
			@Valid @RequestBody ReturnAndAllocateRequest request) {
		return service.returnAndAllocate(user, id, request.requestId(), request.dueOn());
	}

}
