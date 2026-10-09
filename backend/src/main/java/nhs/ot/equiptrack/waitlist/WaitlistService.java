package nhs.ot.equiptrack.waitlist;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nhs.ot.equiptrack.auth.CurrentUser;
import nhs.ot.equiptrack.common.ApiException;
import nhs.ot.equiptrack.common.Dates;
import nhs.ot.equiptrack.equipment.Equipment;
import nhs.ot.equiptrack.equipment.EquipmentLifecycle;
import nhs.ot.equiptrack.equipment.EquipmentRepository;
import nhs.ot.equiptrack.equipment.EquipmentResponse;
import nhs.ot.equiptrack.equipment.EquipmentService;
import nhs.ot.equiptrack.equipment.EquipmentStatus;
import nhs.ot.equiptrack.equipment.EquipmentViews;

@Service
public class WaitlistService {

	public record Entry(long id, String ward, String type, long days, boolean availableNow) {
	}

	private final WaitlistRepository requests;
	private final EquipmentRepository equipment;
	private final EquipmentService equipmentService;
	private final EquipmentLifecycle lifecycle;
	private final EquipmentViews views;
	private final Dates dates;

	WaitlistService(WaitlistRepository requests, EquipmentRepository equipment, EquipmentService equipmentService,
			EquipmentLifecycle lifecycle, EquipmentViews views, Dates dates) {
		this.requests = requests;
		this.equipment = equipment;
		this.equipmentService = equipmentService;
		this.lifecycle = lifecycle;
		this.views = views;
		this.dates = dates;
	}

	// Open requests, oldest first. Walking that order, each request takes one
	// available item of its type; it is "available now" if one was left.
	@Transactional(readOnly = true)
	public List<Entry> list() {
		Map<Long, Long> left = new HashMap<>(equipment.countByTypeWithStatus(EquipmentStatus.AVAILABLE).stream()
				.collect(Collectors.toMap(EquipmentRepository.TypeCount::getTypeId, EquipmentRepository.TypeCount::getTotal)));
		return requests.findOpenOldestFirst().stream().map(request -> {
			long typeId = request.getType().getId();
			boolean availableNow = left.getOrDefault(typeId, 0L) > 0;
			if (availableNow) {
				left.merge(typeId, -1L, Long::sum);
			}
			return new Entry(request.getId(), request.getWard().getName(), request.getType().getName(),
					dates.daysSince(request.getRequestedAt()), availableNow);
		}).toList();
	}

	@Transactional
	public EquipmentResponse allocate(CurrentUser user, long requestId, String equipmentId, LocalDate dueOn) {
		WaitlistRequest request = requests.findById(requestId).orElseThrow(() -> ApiException.notFound("Request not found"));
		if (request.getStatus() != WaitlistRequest.Status.OPEN) {
			throw ApiException.conflict("This request has already been " + request.getStatus().name().toLowerCase());
		}
		Equipment item = equipmentService.find(equipmentId);
		if (item.getStatus() != EquipmentStatus.AVAILABLE || !item.getType().getId().equals(request.getType().getId())) {
			throw ApiException.conflict(item.getLabelCode() + " can't be allocated to this request");
		}
		lifecycle.issue(user, item, request.getWard(), request.getPatient(), request.getId(), dueOn);
		request.allocate(item.getId(), user.id(), dates.now());
		return views.of(item);
	}

}
