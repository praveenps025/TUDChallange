package nhs.ot.equiptrack.discharge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import nhs.ot.equiptrack.ApiTestSupport;
import nhs.ot.equiptrack.user.AppUser;
import nhs.ot.equiptrack.user.Role;

class DischargeIntegrationTest extends ApiTestSupport {

	@Test
	void findsPatientByLastFourDigitsWithLoanedItems() throws Exception {
		TypeRef type = newType();
		PatientRef patient = newPatient("Ward 4C", today());
		String loaned = issuedEquipment(type, "Ward 4C", patient.id(), today().plusDays(2));
		issuedEquipment(type, "Ward 4C", null, today()); // someone else's loan

		mvc.perform(withToken(get("/discharge/" + patient.suffix()), tokenFor(Role.OT_STAFF)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mrn").value(patient.suffix()))
				.andExpect(jsonPath("$.ward").value("Ward 4C"))
				.andExpect(jsonPath("$.bed").value("12"))
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].id").value(loaned))
				.andExpect(jsonPath("$.items[0].status").value("issued"))
				.andExpect(jsonPath("$.override").doesNotExist());
	}

	@Test
	void fullMrnFindsPatientButIsNeverReturned() throws Exception {
		PatientRef patient = newPatient("Ward 4C", null);
		String formatted = patient.fullMrn().substring(0, 3) + " " + patient.fullMrn().substring(3);

		mvc.perform(withToken(get("/discharge/" + formatted), tokenFor(Role.OT_STAFF)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mrn").value(patient.suffix()))
				.andExpect(content().string(not(containsString(patient.fullMrn()))));
		assertThat(jdbc.queryForObject("select count(*) from patient where mrn_hash = ?", Integer.class, patient.fullMrn())).isZero();
	}

	@Test
	void lookupErrors() throws Exception {
		String token = tokenFor(Role.OT_STAFF);
		PatientRef first = newPatient("Ward 1B", null);
		newPatientWithSuffix(first.suffix(), "Ward 2A", null);

		mvc.perform(withToken(get("/discharge/" + first.suffix()), token))
				.andExpect(status().isConflict())
				.andExpect(content().string("More than one patient has an MRN ending " + first.suffix() + ". Enter the full MRN."));
		mvc.perform(withToken(get("/discharge/" + first.fullMrn()), token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ward").value("Ward 1B"));
		mvc.perform(withToken(get("/discharge/123"), token))
				.andExpect(status().isBadRequest())
				.andExpect(content().string("Enter at least the last 4 digits of the MRN"));
		mvc.perform(withToken(get("/discharge/99999999999"), token))
				.andExpect(status().isNotFound())
				.andExpect(content().string("No patient found with that MRN"));
	}

	@Test
	void requestsOverride() throws Exception {
		AppUser user = createUser(Role.OT_STAFF);
		PatientRef patient = newPatient("Ward 4C", today());
		issuedEquipment(newType(), "Ward 4C", patient.id(), today());
		String token = tokenFor(user);

		mvc.perform(jsonBody(withToken(post("/discharge/" + patient.suffix() + "/override"), token), "reason", "  Transferred with chair  "))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mrn").value(patient.suffix()))
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.override.reason").value("Transferred with chair"))
				.andExpect(jsonPath("$.override.status").value("requested"));

		assertThat(jdbc.queryForObject("select requested_by from discharge_override where patient_id = ?", Long.class, patient.id()))
				.isEqualTo(user.getId());
		assertThat(jdbc.queryForObject("select count(*) from activity_log where user_id = ? and detail = ?", Integer.class,
				user.getId(), "Ward 4C · MRN ••••" + patient.suffix())).isEqualTo(1);

		mvc.perform(withToken(get("/discharge/" + patient.suffix()), token))
				.andExpect(jsonPath("$.override.reason").value("Transferred with chair"));
		mvc.perform(jsonBody(withToken(post("/discharge/" + patient.suffix() + "/override"), token), "reason", "Again"))
				.andExpect(status().isConflict())
				.andExpect(content().string("An override for this patient is already waiting for a supervisor"));
	}

	@Test
	void overrideErrors() throws Exception {
		String token = tokenFor(Role.OT_STAFF);
		PatientRef patient = newPatient("Ward 4C", null);
		mvc.perform(jsonBody(withToken(post("/discharge/" + patient.suffix() + "/override"), token), "reason", "   "))
				.andExpect(status().isBadRequest())
				.andExpect(content().string("A reason is required"));
		mvc.perform(jsonBody(withToken(post("/discharge/" + patient.suffix() + "/override"), token)))
				.andExpect(status().isBadRequest());
		mvc.perform(jsonBody(withToken(post("/discharge/99999999999/override"), token), "reason", "x"))
				.andExpect(status().isNotFound());
	}

}
