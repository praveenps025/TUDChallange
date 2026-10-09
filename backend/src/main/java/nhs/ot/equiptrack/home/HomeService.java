package nhs.ot.equiptrack.home;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nhs.ot.equiptrack.activity.ActivityService;
import nhs.ot.equiptrack.auth.CurrentUser;
import nhs.ot.equiptrack.common.Dates;
import nhs.ot.equiptrack.discharge.PatientRepository;
import nhs.ot.equiptrack.equipment.EquipmentRepository;
import nhs.ot.equiptrack.equipment.EquipmentStatus;
import nhs.ot.equiptrack.equipment.EquipmentViews;
import nhs.ot.equiptrack.equipment.LoanRepository;
import nhs.ot.equiptrack.waitlist.WaitlistRepository;
import nhs.ot.equiptrack.waitlist.WaitlistRequest;

@Service
public class HomeService {

	public record Task(String icon, String title, String detail, String to) {
	}

	public record Activity(String icon, String title, String detail, String time) {
	}

	public record HomeResponse(String user, String team, List<Task> tasks, List<Activity> activity) {
	}

	private static final int MAX_PER_KIND = 5;
	private static final int ACTIVITY_COUNT = 5;

	private final WaitlistRepository requests;
	private final EquipmentRepository equipment;
	private final LoanRepository loans;
	private final PatientRepository patients;
	private final ActivityService activity;
	private final EquipmentViews views;
	private final Dates dates;
	private final int unseenDays;

	HomeService(WaitlistRepository requests, EquipmentRepository equipment, LoanRepository loans, PatientRepository patients,
			ActivityService activity, EquipmentViews views, Dates dates, @Value("${app.home.unseen-days}") int unseenDays) {
		this.requests = requests;
		this.equipment = equipment;
		this.loans = loans;
		this.patients = patients;
		this.activity = activity;
		this.views = views;
		this.dates = dates;
		this.unseenDays = unseenDays;
	}

	@Transactional(readOnly = true)
	public HomeResponse home(CurrentUser user) {
		List<Task> tasks = new ArrayList<>();
		addAllocations(tasks);
		addLoansDue(tasks);
		addUnseen(tasks);
		addDischarges(tasks);

		List<Activity> recent = activity.latest(ACTIVITY_COUNT).stream()
				.map(a -> new Activity(a.getKind().name().toLowerCase(), a.getTitle(), a.getDetail(), dates.time(a.getOccurredAt())))
				.toList();
		return new HomeResponse(user.name().split(" ")[0], user.team(), tasks, recent);
	}

	// An available item and someone waiting for that type: one task per type, for the oldest request
	private void addAllocations(List<Task> tasks) {
		Set<Long> types = new HashSet<>();
		for (WaitlistRequest request : requests.findOpenOldestFirst()) {
			if (!types.add(request.getType().getId())) {
				continue;
			}
			equipment.findFirstByTypeIdAndStatusOrderByLabelCode(request.getType().getId(), EquipmentStatus.AVAILABLE)
					.ifPresent(item -> tasks.add(new Task("layers", "Allocate " + request.getType().getName().toLowerCase(),
							item.getLabelCode() + " · " + request.getWard().getName() + " is next",
							"/waitlist?allocate=" + item.getLabelCode())));
		}
	}

	private void addLoansDue(List<Task> tasks) {
		loans.findOpenDueBy(dates.endOf(dates.today())).stream().limit(MAX_PER_KIND).forEach(loan -> {
			String label = loan.getEquipment().getLabelCode();
			tasks.add(new Task("clock", loan.isOverdue(dates.now()) ? "Loan overdue" : "Loan due",
					label + " · " + loan.getWard().getName() + " · " + views.due(loan), "/item/" + label));
		});
	}

	private void addUnseen(List<Task> tasks) {
		long unseen = equipment.countUnseenSince(dates.now().minus(Duration.ofDays(unseenDays)));
		if (unseen > 0) {
			tasks.add(new Task("eyeoff", "Unseen items",
					unseen + (unseen == 1 ? " item" : " items") + " · Last seen over " + unseenDays + " days ago", "/audit"));
		}
	}

	private void addDischarges(List<Task> tasks) {
		patients.findDueForDischargeWithOpenLoans(dates.today()).stream().limit(MAX_PER_KIND).forEach(patient ->
				tasks.add(new Task("clipboard", "Discharge check",
						patient.getWard().getName() + " · MRN ••••" + patient.getMrnSuffix(),
						"/discharge?mrn=" + patient.getMrnSuffix())));
	}

}
