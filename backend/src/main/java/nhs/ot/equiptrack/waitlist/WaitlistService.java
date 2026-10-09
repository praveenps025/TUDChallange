package nhs.ot.equiptrack.waitlist;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nhs.ot.equiptrack.activity.ActivityLog;
import nhs.ot.equiptrack.activity.ActivityService;
import nhs.ot.equiptrack.auth.CurrentUser;
import nhs.ot.equiptrack.common.ApiException;
import nhs.ot.equiptrack.common.Dates;
import nhs.ot.equiptrack.discharge.DischargeService;
import nhs.ot.equiptrack.discharge.Patient;
import nhs.ot.equiptrack.equipment.Equipment;
import nhs.ot.equiptrack.equipment.EquipmentLifecycle;
import nhs.ot.equiptrack.equipment.EquipmentRepository;
import nhs.ot.equiptrack.equipment.EquipmentResponse;
import nhs.ot.equiptrack.equipment.EquipmentService;
import nhs.ot.equiptrack.equipment.EquipmentStatus;
import nhs.ot.equiptrack.equipment.EquipmentType;
import nhs.ot.equiptrack.equipment.EquipmentViews;
import nhs.ot.equiptrack.equipment.Location;
import nhs.ot.equiptrack.equipment.LocationRepository;
import nhs.ot.equiptrack.equipment.TypeRepository;

@Service
public class WaitlistService {

	public record Entry(long id, String ward, String type, long days, boolean availableNow) {
	}

	// mrn (optional): the last four digits of a known patient, or the full MRN
	public record NewRequest(String ward, String type, String mrn, String bed) {
	}

	private final WaitlistRepository requests;
	private final EquipmentRepository equipment;
	private final EquipmentService equipmentService;
	private final EquipmentLifecycle lifecycle;
	private final EquipmentViews views;
	private final LocationRepository locations;
	private final TypeRepository types;
	private final DischargeService patients;
	private final ActivityService activity;
	private final Dates dates;

	WaitlistService(WaitlistRepository requests, EquipmentRepository equipment, EquipmentService equipmentService,
			EquipmentLifecycle lifecycle, EquipmentViews views, LocationRepository locations, TypeRepository types,
			DischargeService patients, ActivityService activity, Dates dates) {
		this.requests = requests;
		this.equipment = equipment;
		this.equipmentService = equipmentService;
		this.lifecycle = lifecycle;
		this.views = views;
		this.locations = locations;
		this.types = types;
		this.patients = patients;
		this.activity = activity;
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
	public Entry create(CurrentUser user, NewRequest input) {
		Location ward = locations.findByName(input.ward().trim())
				.filter(l -> l.getKind().equals("ward") && l.isActive())
				.orElseThrow(() -> ApiException.badRequest("Unknown ward " + input.ward()));
		EquipmentType type = types.findByName(input.type().trim())
				.orElseThrow(() -> ApiException.badRequest("Unknown equipment type " + input.type()));
		Patient patient = input.mrn() == null || input.mrn().isBlank() ? null : patients.findOrCreate(input.mrn(), ward, input.bed());

		WaitlistRequest request = requests.save(new WaitlistRequest(ward, type, patient, user.id(), dates.now()));
		activity.log(ActivityLog.Kind.CLIPBOARD, type.getName() + " requested", ward.getName(), user, null);
		return list().stream().filter(e -> e.id() == request.getId()).findFirst().orElseThrow();
	}

	@Transactional
	public void cancel(CurrentUser user, long requestId) {
		WaitlistRequest request = findOpen(requestId);
		request.cancel(user.id(), dates.now());
		activity.log(ActivityLog.Kind.CLIPBOARD, "Request cancelled",
				request.getType().getName() + " · " + request.getWard().getName(), user, null);
	}

	@Transactional
	public EquipmentResponse allocate(CurrentUser user, long requestId, String equipmentId, LocalDate dueOn) {
		WaitlistRequest request = findOpen(requestId);
		Equipment item = equipmentService.find(equipmentId);
		issue(user, request, item, dueOn);
		return views.of(item);
	}

	// Return check: take the item back and hand it straight to the next ward,
	// in one transaction, so it is never left returned but unallocated
	@Transactional
	public EquipmentResponse returnAndAllocate(CurrentUser user, String equipmentId, long requestId, LocalDate dueOn) {
		WaitlistRequest request = findOpen(requestId);
		Equipment item = equipmentService.find(equipmentId);
		lifecycle.returnItem(user, item, true);
		issue(user, request, item, dueOn);
		return views.of(item);
	}

	private void issue(CurrentUser user, WaitlistRequest request, Equipment item, LocalDate dueOn) {
		if (item.getStatus() != EquipmentStatus.AVAILABLE || !item.getType().getId().equals(request.getType().getId())) {
			throw ApiException.conflict(item.getLabelCode() + " can't be allocated to this request");
		}
		lifecycle.issue(user, item, request.getWard(), request.getPatient(), request.getId(), dueOn);
		request.allocate(item.getId(), user.id(), dates.now());
	}

	private WaitlistRequest findOpen(long requestId) {
		WaitlistRequest request = requests.findById(requestId).orElseThrow(() -> ApiException.notFound("Request not found"));
		if (request.getStatus() != WaitlistRequest.Status.OPEN) {
			throw ApiException.conflict("This request has already been " + request.getStatus().name().toLowerCase());
		}
		return request;
	}

}
