package nhs.ot.equiptrack.equipment;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import nhs.ot.equiptrack.activity.ActivityLog;
import nhs.ot.equiptrack.activity.ActivityService;
import nhs.ot.equiptrack.auth.CurrentUser;
import nhs.ot.equiptrack.common.ApiException;
import nhs.ot.equiptrack.common.Dates;
import nhs.ot.equiptrack.discharge.Patient;

// The only place an item's status changes. Allowed transitions:
//
//   available --issue--> issued --return (clean)-----> available
//                                 --return (not clean)-> inspect --inspect--> available
//   any (not found when an audit walk finishes) -------> missing
//   missing --scanned on an audit walk---------------> inspect
//
// Each method runs inside the caller's transaction and logs one activity entry.
// Two people acting on the same item at once are caught by the @Version
// column on Equipment and answered with 409.
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class EquipmentLifecycle {

	private final LoanRepository loans;
	private final LocationRepository locations;
	private final ActivityService activity;
	private final EquipmentProperties props;
	private final Dates dates;

	EquipmentLifecycle(LoanRepository loans, LocationRepository locations, ActivityService activity,
			EquipmentProperties props, Dates dates) {
		this.loans = loans;
		this.locations = locations;
		this.activity = activity;
		this.props = props;
		this.dates = dates;
	}

	public void returnItem(CurrentUser user, Equipment item, boolean clean) {
		if (item.getStatus() != EquipmentStatus.ISSUED) {
			throw ApiException.conflict(item.getLabelCode() + " is not issued");
		}
		Instant now = dates.now();
		loans.findOpen(item.getId()).ifPresent(loan -> loan.close(user.id(), now, clean));
		Location store = locations.findByName(props.returnLocation())
				.orElseThrow(() -> new IllegalStateException("Return location not found: " + props.returnLocation()));
		item.moveTo(clean ? EquipmentStatus.AVAILABLE : EquipmentStatus.INSPECT, store, now);
		item.seen(now);
		activity.log(ActivityLog.Kind.IN, item.getName() + " returned", item.getLabelCode() + " · " + store.getName(), user, item.getId());
		// Write the closed loan now: Hibernate inserts before it updates, so a new
		// loan in the same transaction would otherwise clash with "one open loan per item"
		loans.flush();
	}

	public void inspect(CurrentUser user, Equipment item) {
		if (item.getStatus() != EquipmentStatus.INSPECT) {
			throw ApiException.conflict(item.getLabelCode() + " doesn't need inspection");
		}
		Instant now = dates.now();
		item.moveTo(EquipmentStatus.AVAILABLE, item.getLocation(), now);
		item.seen(now);
		activity.log(ActivityLog.Kind.CLIPBOARD, item.getName() + " inspected", item.getLabelCode() + " · Available", user, item.getId());
	}

	// An item was scanned on an audit walk. A missing item has turned up and
	// is checked before it is used again. Returns true if it had been missing.
	public boolean sighted(CurrentUser user, Equipment item) {
		Instant now = dates.now();
		item.seen(now);
		if (item.getStatus() != EquipmentStatus.MISSING) {
			return false;
		}
		item.moveTo(EquipmentStatus.INSPECT, item.getLocation(), now);
		activity.log(ActivityLog.Kind.CLIPBOARD, item.getName() + " found", item.getLabelCode() + " · Needs inspection", user, item.getId());
		return true;
	}

	// Expected on an audit walk but not found. Any open loan stays open.
	public void markMissing(CurrentUser user, Equipment item) {
		if (item.getStatus() == EquipmentStatus.MISSING) {
			return;
		}
		item.moveTo(EquipmentStatus.MISSING, item.getLocation(), dates.now());
		activity.log(ActivityLog.Kind.CLIPBOARD, item.getName() + " reported missing",
				item.getLabelCode() + " · Expected in " + item.getLocation().getName(), user, item.getId());
	}

	// dueOn is optional: staff may set it, otherwise the type's default loan length applies
	public Loan issue(CurrentUser user, Equipment item, Location ward, Patient patient, Long waitlistRequestId, LocalDate dueOn) {
		if (item.getStatus() != EquipmentStatus.AVAILABLE) {
			throw ApiException.conflict(item.getLabelCode() + " is not available");
		}
		LocalDate today = dates.today();
		LocalDate due = dueOn != null ? dueOn : today.plusDays(item.getType().getDefaultLoanDays());
		if (due.isBefore(today)) {
			throw ApiException.badRequest("The due date can't be in the past");
		}
		Instant now = dates.now();
		Loan loan = loans.save(new Loan(item, patient, ward, waitlistRequestId, user.id(), now, dates.endOf(due)));
		item.moveTo(EquipmentStatus.ISSUED, ward, now);
		item.seen(now);
		activity.log(ActivityLog.Kind.OUT, item.getName() + " issued", item.getLabelCode() + " · " + ward.getName(), user, item.getId());
		return loan;
	}

}
