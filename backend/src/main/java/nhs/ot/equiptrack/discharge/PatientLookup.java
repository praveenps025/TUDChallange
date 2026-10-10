package nhs.ot.equiptrack.discharge;

import java.util.List;

import org.springframework.stereotype.Component;

import nhs.ot.equiptrack.common.ApiException;

// Finds a patient from what staff type: usually the last four MRN digits, or
// the full MRN when two patients share them
@Component
public class PatientLookup {

	private final PatientRepository patients;
	private final MrnHasher hasher;

	PatientLookup(PatientRepository patients, MrnHasher hasher) {
		this.patients = patients;
		this.hasher = hasher;
	}

	public Patient resolve(String mrn) {
		String digits = MrnHasher.digits(mrn);
		if (digits.length() < 4) {
			throw ApiException.badRequest("Enter at least the last 4 digits of the MRN");
		}
		if (digits.length() > 4) {
			return patients.findByMrnHash(hasher.hash(digits)).orElseThrow(PatientLookup::notFound);
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

	private static ApiException notFound() {
		return ApiException.notFound("No patient found with that MRN");
	}

}
