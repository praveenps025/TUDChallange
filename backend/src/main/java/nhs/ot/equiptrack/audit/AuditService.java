package nhs.ot.equiptrack.audit;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nhs.ot.equiptrack.common.ApiException;
import nhs.ot.equiptrack.common.Dates;
import nhs.ot.equiptrack.equipment.EquipmentRepository;
import nhs.ot.equiptrack.equipment.EquipmentResponse;
import nhs.ot.equiptrack.equipment.EquipmentViews;

@Service
public class AuditService {

	// "missing" here means "still to find on this walk", as the Audit screen shows it
	public record WalkResponse(String area, String started, String by, long total, long found, List<EquipmentResponse> missing) {
	}

	private final AuditWalkRepository walks;
	private final EquipmentRepository equipment;
	private final EquipmentViews views;
	private final Dates dates;

	AuditService(AuditWalkRepository walks, EquipmentRepository equipment, EquipmentViews views, Dates dates) {
		this.walks = walks;
		this.equipment = equipment;
		this.views = views;
		this.dates = dates;
	}

	@Transactional(readOnly = true)
	public WalkResponse current() {
		AuditWalk walk = walks.findFirstByStatusOrderByStartedAtDescIdDesc(AuditWalk.Status.OPEN)
				.orElseThrow(() -> ApiException.notFound("No audit walk is in progress"));
		List<EquipmentResponse> stillToFind = views.of(equipment.findByIdIn(walks.findNotYetFound(walk.getId()))).stream()
				.sorted(Comparator.comparing(EquipmentResponse::id))
				.toList();
		String by = walk.getStartedBy() == null ? "OT team" : walk.getStartedBy().getDisplayName();
		return new WalkResponse(walk.getArea(), dates.clockTime(walk.getStartedAt()), by,
				walks.countExpected(walk.getId()), walks.countFound(walk.getId()), stillToFind);
	}

}
