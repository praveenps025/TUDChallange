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
import nhs.ot.equiptrack.equipment.Location;
import nhs.ot.equiptrack.equipment.LoanRepository;

@Service
public class DischargeService {

	public record OverrideInfo(String reason, String status) {
	}

	// mrn is only ever the last four digits
	public record PatientResponse(String mrn, String ward, String bed, List<EquipmentResponse> items, OverrideInfo override) {
	}

	private final PatientRepository patients;
	private final OverrideRepository overrides;
	private final LoanRepository loans;
	private final EquipmentViews views;
	private final ActivityService activity;
	private final MrnHasher hasher;
	private final Dates dates;

	DischargeService(PatientRepository patients, OverrideRepository overrides, LoanRepository loans, EquipmentViews views,
			ActivityService activity, MrnHasher hasher, Dates dates) {
		this.patients = patients;
		this.overrides = overrides;
		this.loans = loans;
		this.views = views;
		this.activity = activity;
		this.hasher = hasher;
		this.dates = dates;
	}

	@Transactional(readOnly = true)
	public PatientResponse find(String mrn) {
		return response(resolve(mrn));
	}

	@Transactional
	public PatientResponse requestOverride(CurrentUser user, String mrn, String reason) {
		Patient patient = resolve(mrn);
		if (overrides.existsByPatientIdAndStatus(patient.getId(), DischargeOverride.Status.REQUESTED)) {
			throw ApiException.conflict("An override for this patient is already waiting for a supervisor");
		}
		overrides.save(new DischargeOverride(patient, reason.trim(), user.id(), dates.now()));
		activity.log(ActivityLog.Kind.CLIPBOARD, "Discharge override requested",
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

	// Staff usually type the last four digits; the full MRN resolves clashes
	Patient resolve(String mrn) {
		String digits = MrnHasher.digits(mrn);
		if (digits.length() < 4) {
			throw ApiException.badRequest("Enter at least the last 4 digits of the MRN");
		}
		if (digits.length() > 4) {
			return patients.findByMrnHash(hasher.hash(digits)).orElseThrow(DischargeService::notFound);
		}
		List<Patient> matches = patients.findByMrnSuffix(digits);
		if (matches.isEmpty()) {
			throw notFound();
		}
		if (matches.size() > 1) {
			throw ApiException.conflict("More than one patient has an MRN ending " + digits + ". Enter the full MRN.");
		}
		return matches.getFirst();
	}

	private PatientResponse response(Patient patient) {
		List<EquipmentResponse> items = views.of(loans.findOpenForPatient(patient.getId()).stream().map(Loan::getEquipment).toList());
		OverrideInfo override = overrides.findFirstByPatientIdOrderByRequestedAtDescIdDesc(patient.getId())
				.map(o -> new OverrideInfo(o.getReason(), o.getStatus().name().toLowerCase()))
				.orElse(null);
		return new PatientResponse(patient.getMrnSuffix(), patient.getWard() == null ? null : patient.getWard().getName(),
				patient.getBed(), items, override);
	}

	private static ApiException notFound() {
		return ApiException.notFound("No patient found with that MRN");
	}

}
