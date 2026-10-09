package nhs.ot.equiptrack.waitlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

import nhs.ot.equiptrack.ApiTestSupport;
import nhs.ot.equiptrack.user.AppUser;
import nhs.ot.equiptrack.user.Role;

class WaitlistManagementIntegrationTest extends ApiTestSupport {

	@Test
	void createsRequest() throws Exception {
		AppUser user = createUser(Role.OT_STAFF);
		TypeRef type = newType();
		newEquipment(type, "available", "OT store");

		String body = mvc.perform(jsonBody(withToken(post("/waitlist"), tokenFor(user)), "ward", "Ward 3A", "type", type.name()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.ward").value("Ward 3A"))
				.andExpect(jsonPath("$.type").value(type.name()))
				.andExpect(jsonPath("$.days").value(0))
				.andExpect(jsonPath("$.availableNow").value(true))
				.andReturn().getResponse().getContentAsString();

		long id = ((Number) JsonPath.read(body, "$.id")).longValue();
		assertThat(jdbc.queryForMap("select requested_by, patient_id, status from waitlist_request where id = ?", id))
				.containsEntry("requested_by", user.getId()).containsEntry("status", "open").containsEntry("patient_id", null);
	}

	@Test
	void requestForANewPatientCreatesThemFromTheFullMrn() throws Exception {
		TypeRef type = newType();
		String token = tokenFor(Role.OT_STAFF);
		String fullMrn = "71" + String.format("%08d", (long) (Math.random() * 1e8));
		String suffix = fullMrn.substring(fullMrn.length() - 4);

		String body = mvc.perform(jsonBody(withToken(post("/waitlist"), token), "ward", "Ward 4A", "type", type.name(),
				"mrn", fullMrn, "bed", "07"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(body, "$.id")).longValue();

		Map<String, Object> patient = jdbc.queryForMap("""
				select p.mrn_suffix, p.mrn_hash, p.bed from waitlist_request w join patient p on p.id = w.patient_id where w.id = ?
				""", id);
		assertThat(patient).containsEntry("mrn_suffix", suffix).containsEntry("mrn_hash", mrnHasher.hash(fullMrn)).containsEntry("bed", "07");

		// The same patient again, now by the full MRN: no duplicate patient
		mvc.perform(jsonBody(withToken(post("/waitlist"), token), "ward", "Ward 4A", "type", type.name(), "mrn", fullMrn))
				.andExpect(status().isCreated());
		assertThat(jdbc.queryForObject("select count(*) from patient where mrn_hash = ?", Integer.class, mrnHasher.hash(fullMrn))).isEqualTo(1);
	}

	@Test
	void knownPatientCanBeGivenByLastFourDigits() throws Exception {
		TypeRef type = newType();
		PatientRef patient = newPatient("Ward 4C", null);
		String body = mvc.perform(jsonBody(withToken(post("/waitlist"), tokenFor(Role.OT_STAFF)),
				"ward", "Ward 4C", "type", type.name(), "mrn", patient.suffix()))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long id = ((Number) JsonPath.read(body, "$.id")).longValue();
		assertThat(jdbc.queryForObject("select patient_id from waitlist_request where id = ?", Long.class, id)).isEqualTo(patient.id());
	}

	@Test
	void createErrors() throws Exception {
		String token = tokenFor(Role.OT_STAFF);
		TypeRef type = newType();
		mvc.perform(jsonBody(withToken(post("/waitlist"), token), "ward", "OT store", "type", type.name()))
				.andExpect(status().isBadRequest()).andExpect(content().string("Unknown ward OT store"));
		mvc.perform(jsonBody(withToken(post("/waitlist"), token), "ward", "Ward 3A", "type", "Hoist"))
				.andExpect(status().isBadRequest()).andExpect(content().string("Unknown equipment type Hoist"));
		mvc.perform(jsonBody(withToken(post("/waitlist"), token), "type", type.name()))
				.andExpect(status().isBadRequest()).andExpect(content().string("Choose a ward"));
		mvc.perform(jsonBody(withToken(post("/waitlist"), token), "ward", "Ward 3A", "type", type.name(), "mrn", "12"))
				.andExpect(status().isBadRequest()).andExpect(content().string("Enter at least the last 4 digits of the MRN"));

		PatientRef unknownSuffix = newPatient("Ward 3A", null);
		jdbc.update("delete from patient where id = ?", unknownSuffix.id());
		mvc.perform(jsonBody(withToken(post("/waitlist"), token), "ward", "Ward 3A", "type", type.name(), "mrn", unknownSuffix.suffix()))
				.andExpect(status().isBadRequest()).andExpect(content().string("Enter the full MRN to add a new patient"));
	}

	@Test
	void cancelsRequest() throws Exception {
		AppUser user = createUser(Role.OT_STAFF);
		String token = tokenFor(user);
		long request = newRequest("Ward 3A", newType(), Duration.ofDays(1));

		mvc.perform(withToken(delete("/waitlist/" + request), token)).andExpect(status().isNoContent());
		assertThat(jdbc.queryForMap("select status, closed_by from waitlist_request where id = ?", request))
				.containsEntry("status", "cancelled").containsEntry("closed_by", user.getId());

		mvc.perform(withToken(delete("/waitlist/" + request), token))
				.andExpect(status().isConflict()).andExpect(content().string("This request has already been cancelled"));
		mvc.perform(withToken(delete("/waitlist/999999"), token))
				.andExpect(status().isNotFound());
	}

	@Test
	void returnAndAllocateIsOneStep() throws Exception {
		AppUser user = createUser(Role.OT_STAFF);
		TypeRef type = newType();
		String label = issuedEquipment(type, "Ward 2B", null, today());
		long request = newRequest("Ward 3A", type, Duration.ofDays(3));

		mvc.perform(jsonBody(withToken(post("/equipment/" + label + "/return-and-allocate"), tokenFor(user)), "requestId", request))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("issued"))
				.andExpect(jsonPath("$.location").value("Ward 3A"));

		assertThat(jdbc.queryForList("""
				select w.name || '|' || (l.returned_at is not null) from loan l join location w on w.id = l.ward_id
				where l.equipment_id = (select id from equipment where label_code = ?) order by l.id
				""", String.class, label)).containsExactly("Ward 2B|true", "Ward 3A|false");
		assertThat(jdbc.queryForList("select kind from activity_log where equipment_id = (select id from equipment where label_code = ?) order by id",
				String.class, label)).containsExactly("in", "out");
		assertThat(jdbc.queryForObject("select status from waitlist_request where id = ?", String.class, request)).isEqualTo("allocated");
	}

	@Test
	void returnAndAllocateChangesNothingIfAllocationFails() throws Exception {
		String token = tokenFor(Role.OT_STAFF);
		String label = issuedEquipment(newType(), "Ward 2B", null, today());
		long otherTypeRequest = newRequest("Ward 3A", newType(), Duration.ofDays(1));

		mvc.perform(jsonBody(withToken(post("/equipment/" + label + "/return-and-allocate"), token), "requestId", otherTypeRequest))
				.andExpect(status().isConflict())
				.andExpect(content().string(label + " can't be allocated to this request"));
		assertThat(statusOf(label)).isEqualTo("issued");
		assertThat(jdbc.queryForObject("select count(*) from loan where returned_at is null and equipment_id = (select id from equipment where label_code = ?)",
				Integer.class, label)).isEqualTo(1);

		mvc.perform(jsonBody(withToken(post("/equipment/" + label + "/return-and-allocate"), token)))
				.andExpect(status().isBadRequest()).andExpect(content().string("Choose the request to allocate to"));
		mvc.perform(jsonBody(withToken(post("/equipment/" + label + "/return-and-allocate"), token), "requestId", 999999))
				.andExpect(status().isNotFound());
	}

}
