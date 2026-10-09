package nhs.ot.equiptrack.activity;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import nhs.ot.equiptrack.auth.CurrentUser;
import nhs.ot.equiptrack.common.Dates;

// The activity feed on Home. Every state-changing call writes one entry,
// inside the same transaction as the change, naming the signed-in user.
@Service
public class ActivityService {

	private final ActivityRepository repository;
	private final Dates dates;

	public ActivityService(ActivityRepository repository, Dates dates) {
		this.repository = repository;
		this.dates = dates;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void log(ActivityLog.Kind kind, String title, String detail, CurrentUser user, Long equipmentId) {
		repository.save(new ActivityLog(kind, title, detail, user.id(), equipmentId, dates.now()));
	}

	public List<ActivityLog> latest(int count) {
		return repository.findByOrderByOccurredAtDescIdDesc(Limit.of(count));
	}

	public List<Long> recentEquipmentIds(CurrentUser user, int count) {
		return repository.findRecentEquipmentIds(user.id(), count);
	}

}
