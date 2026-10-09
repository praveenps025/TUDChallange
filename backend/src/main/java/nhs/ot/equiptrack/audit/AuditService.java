package nhs.ot.equiptrack.audit;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nhs.ot.equiptrack.activity.ActivityLog;
import nhs.ot.equiptrack.activity.ActivityService;
import nhs.ot.equiptrack.auth.CurrentUser;
import nhs.ot.equiptrack.common.ApiException;
import nhs.ot.equiptrack.common.Dates;
import nhs.ot.equiptrack.equipment.Equipment;
import nhs.ot.equiptrack.equipment.EquipmentLifecycle;
import nhs.ot.equiptrack.equipment.EquipmentRepository;
import nhs.ot.equiptrack.equipment.EquipmentResponse;
import nhs.ot.equiptrack.equipment.EquipmentService;
import nhs.ot.equiptrack.equipment.EquipmentViews;
import nhs.ot.equiptrack.equipment.Location;
import nhs.ot.equiptrack.equipment.LocationRepository;
import nhs.ot.equiptrack.user.UserRepository;

// One walk is in progress at a time, shared by everyone on the team.
//   start:  pick locations; the items there now become the expected list
//   scan:   each item found is recorded (a missing item found goes to inspection)
//   finish: expected items never scanned are marked missing
@Service
public class AuditService {

	// "missing" here means "still to find on this walk", as the Audit screen shows it
	public record WalkResponse(String area, String started, String by, long total, long found, List<EquipmentResponse> missing) {
	}

	public record FinishResponse(String area, long total, long found, long markedMissing) {
	}

	private final AuditWalkRepository walks;
	private final EquipmentRepository equipment;
	private final EquipmentService equipmentService;
	private final EquipmentLifecycle lifecycle;
	private final EquipmentViews views;
	private final LocationRepository locations;
	private final UserRepository users;
	private final ActivityService activity;
	private final Dates dates;

	AuditService(AuditWalkRepository walks, EquipmentRepository equipment, EquipmentService equipmentService,
			EquipmentLifecycle lifecycle, EquipmentViews views, LocationRepository locations, UserRepository users,
			ActivityService activity, Dates dates) {
		this.walks = walks;
		this.equipment = equipment;
		this.equipmentService = equipmentService;
		this.lifecycle = lifecycle;
		this.views = views;
		this.locations = locations;
		this.users = users;
		this.activity = activity;
		this.dates = dates;
	}

	@Transactional(readOnly = true)
	public WalkResponse current() {
		return response(currentWalk("No audit walk is in progress", false));
	}

	// area is optional; it defaults to the location names
	@Transactional
	public WalkResponse start(CurrentUser user, List<String> locationNames, String area) {
		if (walks.findFirstByStatusOrderByStartedAtDescIdDesc(AuditWalk.Status.OPEN).isPresent()) {
			throw ApiException.conflict("An audit walk is already in progress. Finish it before starting another.");
		}
		List<Location> chosen = new LinkedHashSet<>(locationNames).stream()
				.map(name -> locations.findByName(name.trim()).filter(Location::isActive)
						.orElseThrow(() -> ApiException.badRequest("Unknown location " + name)))
				.toList();
		String title = area == null || area.isBlank()
				? String.join(" · ", chosen.stream().map(Location::getName).toList())
				: area.trim();

		AuditWalk walk = walks.save(new AuditWalk(title, users.getReferenceById(user.id()), dates.now()));
		chosen.forEach(location -> walks.addLocation(walk.getId(), location.getId()));
		walks.captureExpectedItems(walk.getId());
		activity.log(ActivityLog.Kind.CLIPBOARD, "Audit walk started", title, user, null);
		return response(walk);
	}

	@Transactional
	public WalkResponse scan(CurrentUser user, String equipmentId) {
		AuditWalk walk = currentWalk("No audit walk is in progress. Start one first.", true);
		Equipment item = equipmentService.find(equipmentId);
		boolean firstScan = walks.recordScan(walk.getId(), item.getId(), user.id()) > 0;
		boolean wasMissing = lifecycle.sighted(user, item);
		if (firstScan && !wasMissing) {
			activity.log(ActivityLog.Kind.CLIPBOARD, item.getName() + " checked", item.getLabelCode() + " · " + walk.getArea(), user, item.getId());
		}
		return response(walk);
	}

	@Transactional
	public FinishResponse finish(CurrentUser user) {
		AuditWalk walk = currentWalk("No audit walk is in progress", true);
		long total = walks.countExpected(walk.getId());
		long found = walks.countFound(walk.getId());
		List<Equipment> notFound = equipment.findByIdIn(walks.findNotYetFound(walk.getId()));
		notFound.forEach(item -> lifecycle.markMissing(user, item));
		walk.close(user.id(), dates.now());
		activity.log(ActivityLog.Kind.CLIPBOARD, "Audit walk finished", walk.getArea() + " · " + found + " of " + total + " found", user, null);
		return new FinishResponse(walk.getArea(), total, found, notFound.size());
	}

	private AuditWalk currentWalk(String messageIfNone, boolean conflict) {
		return walks.findFirstByStatusOrderByStartedAtDescIdDesc(AuditWalk.Status.OPEN)
				.orElseThrow(() -> conflict ? ApiException.conflict(messageIfNone) : ApiException.notFound(messageIfNone));
	}

	private WalkResponse response(AuditWalk walk) {
		List<EquipmentResponse> stillToFind = views.of(equipment.findByIdIn(walks.findNotYetFound(walk.getId()))).stream()
				.sorted(Comparator.comparing(EquipmentResponse::id))
				.toList();
		String by = walk.getStartedBy() == null ? "OT team" : walk.getStartedBy().getDisplayName();
		return new WalkResponse(walk.getArea(), dates.clockTime(walk.getStartedAt()), by,
				walks.countExpected(walk.getId()), walks.countFound(walk.getId()), stillToFind);
	}

}
