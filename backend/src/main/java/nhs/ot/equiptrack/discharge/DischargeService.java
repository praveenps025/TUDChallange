package nhs.ot.equiptrack.discharge;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import nhs.ot.equiptrack.activity.ActivityLog;
import nhs.ot.equiptrack.activity.ActivityService;
import nhs.ot.equiptrack.auth.CurrentUser;
import nhs.ot.equiptrack.common.ApiException;
import nhs.ot.equiptrack.common.Dates;
import nhs.ot.equiptrack.equipment.EquipmentResponse;
import nhs.ot.equiptrack.equipment.EquipmentViews;
import nhs.ot.equiptrack.equipment.Loan;
import nhs.ot.equiptrack.equipment.LoanRepository;
import nhs.ot.equiptrack.equipment.Location;
import nhs.ot.equiptrack.homeloan.HomeLoanInfo;
import nhs.ot.equiptrack.homeloan.HomeLoanRepository;
import nhs.ot.equiptrack.user.AppUser;
import nhs.ot.equiptrack.user.UserRepository;

// Discharge checks. Staff request an override with a reason and a manager
// approves or rejects it with a reason; a manager doing the check can approve
// straight away.
@Service
public class DischargeService {

	// id is needed to approve or reject; decidedBy and decisionReason are set once a manager decides
	public record OverrideInfo(long id, String reason, String status, String requestedBy, String decidedBy, String decisionReason) {

		static OverrideInfo of(DischargeOverride o) {
			return new OverrideInfo(o.getId(), o.getReason(), o.getStatus().name().toLowerCase(), name(o.getRequestedBy()),
					name(o.getDecidedBy()), o.getDecisionReason());
		}

		private static String name(AppUser user) {
			return user == null ? null : user.getDisplayName();
		}
	}

	// mrn is only ever the last four digits. Items on a home loan have homeLoan
	// set and no longer hold the discharge; homeLoan is the latest home loan request.
	public record PatientResponse(String mrn, String ward, String bed, List<EquipmentResponse> items, OverrideInfo override,
			HomeLoanInfo homeLoan) {
	}

	public record PendingOverride(long id, String mrn, String ward, String bed, String reason, String requestedBy,
			String requestedAt, int itemsOut) {
	}

	private final PatientRepository patients;
	private final OverrideRepository overrides;
	private final LoanRepository loans;
	private final UserRepository users;
	private final EquipmentViews views;
	private final ActivityService activity;
	private final MrnHasher hasher;
	private final PatientLookup lookup;
	private final HomeLoanRepository homeLoans;
	private final Dates dates;

	DischargeService(PatientRepository patients, OverrideRepository overrides, LoanRepository loans, UserRepository users,
			EquipmentViews views, ActivityService activity, MrnHasher hasher, PatientLookup lookup, HomeLoanRepository homeLoans,
			Dates dates) {
		this.patients = patients;
		this.overrides = overrides;
		this.loans = loans;
		this.users = users;
		this.views = views;
		this.activity = activity;
		this.hasher = hasher;
		this.lookup = lookup;
		this.homeLoans = homeLoans;
		this.dates = dates;
	}

	@Transactional(readOnly = true)
	public PatientResponse find(String mrn) {
		return response(resolve(mrn));
	}

	// Staff: the request waits for a manager. Manager: approved immediately.
	@Transactional
	public PatientResponse requestOverride(CurrentUser user, String mrn, String reason) {
		Patient patient = resolve(mrn);
		if (overrides.existsByPatientIdAndStatus(patient.getId(), DischargeOverride.Status.REQUESTED)) {
			throw ApiException.conflict("An override for this patient is already waiting for a manager");
		}
		AppUser me = users.getReferenceById(user.id());
		DischargeOverride override = overrides.save(new DischargeOverride(patient, reason.trim(), me, dates.now()));
		String detail = patient.getWard().getName() + " · MRN ••••" + patient.getMrnSuffix();
		if (user.isManager()) {
			override.decide(true, me, reason.trim(), dates.now());
			activity.log(ActivityLog.Kind.CLIPBOARD, "Discharge approved", detail, user, null);
		} else {
			activity.log(ActivityLog.Kind.CLIPBOARD, "Discharge override requested", detail, user, null);
		}
		return response(patient);
	}

	@Transactional(readOnly = true)
	public List<PendingOverride> pending() {
		return overrides.findByStatusOrderByRequestedAtAsc(DischargeOverride.Status.REQUESTED).stream().map(o -> {
			Patient p = o.getPatient();
			return new PendingOverride(o.getId(), p.getMrnSuffix(), p.getWard() == null ? null : p.getWard().getName(), p.getBed(),
					o.getReason(), OverrideInfo.name(o.getRequestedBy()), dates.time(o.getRequestedAt()),
					loans.findOpenForPatient(p.getId()).size());
		}).toList();
	}

	// Managers only (checked in the controller); a reason is always required
	@Transactional
	public PatientResponse decide(CurrentUser user, long overrideId, boolean approve, String reason) {
		DischargeOverride override = overrides.findById(overrideId)
				.orElseThrow(() -> ApiException.notFound("Override request not found"));
		if (override.getStatus() != DischargeOverride.Status.REQUESTED) {
			throw ApiException.conflict("This request has already been " + override.getStatus().name().toLowerCase());
		}
		override.decide(approve, users.getReferenceById(user.id()), reason.trim(), dates.now());
		Patient patient = override.getPatient();
		activity.log(ActivityLog.Kind.CLIPBOARD, approve ? "Discharge approved" : "Discharge override rejected",
				patient.getWard().getName() + " · MRN ••••" + patient.getMrnSuffix(), user, null);
		return response(patient);
	}

	// The patient a waitlist request is for. A known patient can be given by the
	// last four digits; a new one needs the full MRN so they are unique.
	@Transactional(propagation = Propagation.MANDATORY)
	public Patient findOrCreate(String mrn, Location ward, String bed) {
		String digits = MrnHasher.digits(mrn);
		if (digits.length() < 4) {
			throw ApiException.badRequest("Enter at least the last 4 digits of the MRN");
		}
		if (digits.length() == 4) {
			List<Patient> matches = patients.findByMrnSuffix(digits);
			if (matches.isEmpty()) {
				throw ApiException.badRequest("Enter the full MRN to add a new patient");
			}
			if (matches.size() > 1) {
				throw ApiException.conflict("More than one patient has an MRN ending " + digits + ". Enter the full MRN.");
			}
			return matches.getFirst();
		}
		String hash = hasher.hash(digits);
		return patients.findByMrnHash(hash)
				.orElseGet(() -> patients.save(new Patient(hash, digits.substring(digits.length() - 4), ward, blankToNull(bed))));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private Patient resolve(String mrn) {
		return lookup.resolve(mrn);
	}

	private PatientResponse response(Patient patient) {
		List<EquipmentResponse> items = views.of(loans.findOpenForPatient(patient.getId()).stream().map(Loan::getEquipment).toList());
		OverrideInfo override = overrides.findFirstByPatientIdOrderByRequestedAtDescIdDesc(patient.getId())
				.map(OverrideInfo::of)
				.orElse(null);
		HomeLoanInfo homeLoan = homeLoans.findFirstByPatientIdOrderByRequestedAtDescIdDesc(patient.getId())
				.map(r -> HomeLoanInfo.of(r, dates))
				.orElse(null);
		return new PatientResponse(patient.getMrnSuffix(), patient.getWard() == null ? null : patient.getWard().getName(),
				patient.getBed(), items, override, homeLoan);
	}

}
