package nhs.ot.equiptrack.homeloan;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nhs.ot.equiptrack.activity.ActivityLog;
import nhs.ot.equiptrack.activity.ActivityService;
import nhs.ot.equiptrack.auth.CurrentUser;
import nhs.ot.equiptrack.common.ApiException;
import nhs.ot.equiptrack.common.Dates;
import nhs.ot.equiptrack.discharge.Patient;
import nhs.ot.equiptrack.discharge.PatientLookup;
import nhs.ot.equiptrack.equipment.Equipment;
import nhs.ot.equiptrack.equipment.EquipmentLifecycle;
import nhs.ot.equiptrack.equipment.EquipmentService;
import nhs.ot.equiptrack.equipment.EquipmentStatus;
import nhs.ot.equiptrack.equipment.LoanRepository;
import nhs.ot.equiptrack.user.UserRepository;

// Lending equipment for a patient to take home. Staff request it (from the
// discharge check or an item's page); a manager approves or rejects it with a
// reason, and a manager making the request approves it at once. Contact
// details are deliberately not stored: staff use the hospital system.
@Service
public class HomeLoanService {

	public record ItemInput(String equipmentId, LocalDate dueOn) {
	}

	private final HomeLoanRepository requests;
	private final PatientLookup patients;
	private final EquipmentService equipment;
	private final EquipmentLifecycle lifecycle;
	private final LoanRepository loans;
	private final UserRepository users;
	private final ActivityService activity;
	private final Dates dates;

	HomeLoanService(HomeLoanRepository requests, PatientLookup patients, EquipmentService equipment, EquipmentLifecycle lifecycle,
			LoanRepository loans, UserRepository users, ActivityService activity, Dates dates) {
		this.requests = requests;
		this.patients = patients;
		this.equipment = equipment;
		this.lifecycle = lifecycle;
		this.loans = loans;
		this.users = users;
		this.activity = activity;
		this.dates = dates;
	}

	// dueOn may be left out: the item type's loan length applies
	@Transactional
	public HomeLoanInfo request(CurrentUser user, String mrn, List<ItemInput> items, String note) {
		Patient patient = patients.resolve(mrn);
		if (items == null || items.isEmpty()) {
			throw ApiException.badRequest("Choose at least one item to lend");
		}
		if (requests.existsByPatientIdAndStatus(patient.getId(), HomeLoanRequest.Status.REQUESTED)) {
			throw ApiException.conflict("A home loan for this patient is already waiting for a manager");
		}
		String cleanNote = note == null || note.isBlank() ? null : note.trim();
		HomeLoanRequest request = new HomeLoanRequest(patient, cleanNote, users.getReferenceById(user.id()), dates.now());
		Set<String> seen = new HashSet<>();
		for (ItemInput input : items) {
			Equipment item = equipment.find(input.equipmentId());
			if (!seen.add(item.getLabelCode())) {
				throw ApiException.badRequest(item.getLabelCode() + " is listed twice");
			}
			checkCanLend(item, patient);
			LocalDate due = input.dueOn() != null ? input.dueOn() : dates.today().plusDays(item.getType().getDefaultLoanDays());
			if (due.isBefore(dates.today())) {
				throw ApiException.badRequest("The return date can't be in the past");
			}
			request.add(item, due);
		}
		requests.save(request);
		String detail = items.size() + (items.size() == 1 ? " item" : " items") + " · MRN ••••" + patient.getMrnSuffix();
		activity.log(ActivityLog.Kind.CLIPBOARD, "Home loan requested", detail, user, null);
		if (user.isManager()) {
			apply(user, request, cleanNote == null ? "Approved by the manager who requested it" : cleanNote);
		}
		return HomeLoanInfo.of(request, dates);
	}

	@Transactional(readOnly = true)
	public List<HomeLoanInfo> pending() {
		return requests.findByStatusOrderByRequestedAtAsc(HomeLoanRequest.Status.REQUESTED).stream()
				.map(r -> HomeLoanInfo.of(r, dates))
				.toList();
	}

	// Managers only (checked in the controller); a reason is always required
	@Transactional
	public HomeLoanInfo decide(CurrentUser user, long id, boolean approve, String reason) {
		HomeLoanRequest request = requests.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Home loan request not found"));
		if (request.getStatus() != HomeLoanRequest.Status.REQUESTED) {
			throw ApiException.conflict("This request has already been " + request.getStatus().name().toLowerCase());
		}
		if (approve) {
			apply(user, request, reason.trim());
		} else {
			request.decide(false, users.getReferenceById(user.id()), reason.trim(), dates.now());
			activity.log(ActivityLog.Kind.CLIPBOARD, "Home loan rejected", "MRN ••••" + request.getPatient().getMrnSuffix(), user, null);
		}
		return HomeLoanInfo.of(request, dates);
	}

	// Each item goes home on its loan; nothing changes if any item can no longer be lent
	private void apply(CurrentUser user, HomeLoanRequest request, String reason) {
		Patient patient = request.getPatient();
		for (HomeLoanRequest.Item item : request.getItems()) {
			Equipment locked = equipment.findForUpdate(item.getEquipment().getLabelCode());
			lifecycle.sendHome(user, locked, patient, item.getDueOn(), request.getNote());
		}
		request.decide(true, users.getReferenceById(user.id()), reason, dates.now());
		activity.log(ActivityLog.Kind.CLIPBOARD, "Home loan approved", "MRN ••••" + patient.getMrnSuffix(), user, null);
	}

	// Lendable: in stock, or already lent to this patient's ward
	private void checkCanLend(Equipment item, Patient patient) {
		if (item.getStatus() == EquipmentStatus.AVAILABLE) {
			return;
		}
		boolean onThisPatientsWardLoan = item.getStatus() == EquipmentStatus.ISSUED && loans.findOpen(item.getId())
				.filter(loan -> !loan.isHome() && loan.getPatient() != null && loan.getPatient().getId().equals(patient.getId()))
				.isPresent();
		if (!onThisPatientsWardLoan) {
			throw ApiException.conflict(item.getLabelCode() + " can't be lent: it is " + item.getStatus().value()
					+ " and not on loan to this patient");
		}
	}

}
