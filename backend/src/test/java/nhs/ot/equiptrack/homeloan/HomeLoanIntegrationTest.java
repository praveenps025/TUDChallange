package nhs.ot.equiptrack.homeloan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import nhs.ot.equiptrack.ApiTestSupport;
import nhs.ot.equiptrack.user.AppUser;
import nhs.ot.equiptrack.user.Role;

class HomeLoanIntegrationTest extends ApiTestSupport {

	@Test
	void staffRequestAtDischargeAndManagerApproves() throws Exception {
		AppUser staff = createUser(Role.OT_STAFF);
		AppUser manager = createUser(Role.MANAGER);
		PatientRef patient = newPatient("Ward 4C", today());
		String chair = issuedEquipment(newType(), "Ward 4C", patient.id(), today().plusDays(1));
		LocalDate due = today().plusDays(21);

		long id = requestHomeLoan(tokenFor(staff), patient.suffix(), chair, due, "Community OT to collect", "requested");

		// The discharge check shows the request waiting, and the item still holds the discharge
		mvc.perform(withToken(get("/api/discharge/" + patient.suffix()), tokenFor(staff)))
				.andExpect(jsonPath("$.homeLoan.status").value("requested"))
				.andExpect(jsonPath("$.homeLoan.requestedBy").value(staff.getDisplayName()))
				.andExpect(jsonPath("$.homeLoan.items[0].id").value(chair))
				.andExpect(jsonPath("$.items[0].homeLoan").value(false));

		String pending = mvc.perform(withToken(get("/api/home-loans/pending"), tokenFor(manager)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat((List<?>) JsonPath.read(pending, "$[?(@.id == " + id + ")]")).hasSize(1);

		mvc.perform(jsonBody(withToken(post("/api/home-loans/" + id + "/approve"), tokenFor(manager)), "reason", "Patient needs it at home"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("approved"))
				.andExpect(jsonPath("$.decidedBy").value(manager.getDisplayName()))
				.andExpect(jsonPath("$.decisionReason").value("Patient needs it at home"));

		mvc.perform(withToken(get("/api/discharge/" + patient.suffix()), tokenFor(staff)))
				.andExpect(jsonPath("$.items[0].id").value(chair))
				.andExpect(jsonPath("$.items[0].homeLoan").value(true))
				.andExpect(jsonPath("$.items[0].status").value("issued"))
				.andExpect(jsonPath("$.items[0].location").value("Home loan · MRN ••••" + patient.suffix()))
				.andExpect(jsonPath("$.items[0].note").value("Home loan since today · Due " + day(due)));

		Map<String, Object> loan = jdbc.queryForMap("""
				select home_since is not null as home, home_note, patient_id from loan
				where returned_at is null and equipment_id = (select id from equipment where label_code = ?)
				""", chair);
		assertThat(loan).containsEntry("home", true).containsEntry("home_note", "Community OT to collect")
				.containsEntry("patient_id", patient.id());
	}

	@Test
	void managerRequestIsApprovedAtOnce() throws Exception {
		PatientRef patient = newPatient("Ward 4C", today());
		String chair = issuedEquipment(newType(), "Ward 4C", patient.id(), today());

		requestHomeLoan(tokenFor(Role.MANAGER), patient.suffix(), chair, null, null, "approved");
		mvc.perform(withToken(get("/api/equipment/" + chair), tokenFor(Role.OT_STAFF)))
				.andExpect(jsonPath("$.homeLoan").value(true))
				.andExpect(jsonPath("$.location").value("Home loan · MRN ••••" + patient.suffix()))
				.andExpect(jsonPath("$.note").value("Home loan since today · Due " + day(today().plusDays(14))));
	}

	@Test
	void availableItemCanBeLentStraightFromItsPage() throws Exception {
		PatientRef patient = newPatient("Ward 3A", null);
		String cushion = newEquipment(newType(), "available", "OT store");

		long id = requestHomeLoan(tokenFor(Role.OT_STAFF), patient.fullMrn(), cushion, today().plusDays(10), null, "requested");
		assertThat(statusOf(cushion)).isEqualTo("available"); // nothing moves until a manager approves

		mvc.perform(jsonBody(withToken(post("/api/home-loans/" + id + "/approve"), tokenFor(Role.MANAGER)), "reason", "OK"))
				.andExpect(status().isOk());
		assertThat(statusOf(cushion)).isEqualTo("issued");
		assertThat(jdbc.queryForObject("""
				select patient_id from loan where returned_at is null and home_since is not null
				  and equipment_id = (select id from equipment where label_code = ?)
				""", Long.class, cushion)).isEqualTo(patient.id());
	}

	@Test
	void rejectingLeavesEverythingAsItWas() throws Exception {
		PatientRef patient = newPatient("Ward 4C", today());
		String chair = issuedEquipment(newType(), "Ward 4C", patient.id(), today());
		long id = requestHomeLoan(tokenFor(Role.OT_STAFF), patient.suffix(), chair, null, null, "requested");

		mvc.perform(jsonBody(withToken(post("/api/home-loans/" + id + "/reject"), tokenFor(Role.MANAGER)), "reason", "Bring it back first"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("rejected"))
				.andExpect(jsonPath("$.decisionReason").value("Bring it back first"));
		mvc.perform(withToken(get("/api/equipment/" + chair), tokenFor(Role.OT_STAFF)))
				.andExpect(jsonPath("$.homeLoan").value(false))
				.andExpect(jsonPath("$.location").value("Ward 4C"));
	}

	@Test
	void homeLoanItemIsReturnedLikeAnyOther() throws Exception {
		PatientRef patient = newPatient("Ward 4C", today());
		String chair = issuedEquipment(newType(), "Ward 4C", patient.id(), today());
		requestHomeLoan(tokenFor(Role.MANAGER), patient.suffix(), chair, null, null, "approved");

		mvc.perform(jsonBody(withToken(post("/api/equipment/" + chair + "/return"), tokenFor(Role.OT_STAFF)), "clean", false))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("inspect"))
				.andExpect(jsonPath("$.location").value("OT store"))
				.andExpect(jsonPath("$.homeLoan").value(false));
	}

	@Test
	void errors() throws Exception {
		String staff = tokenFor(Role.OT_STAFF);
		PatientRef patient = newPatient("Ward 4C", today());
		PatientRef someoneElse = newPatient("Ward 4C", today());
		TypeRef type = newType();
		String missing = newEquipment(type, "missing", "Ward 4C");
		String othersChair = issuedEquipment(type, "Ward 4C", someoneElse.id(), today());
		String free = newEquipment(type, "available", "OT store");

		mvc.perform(homeLoan(staff, patient.suffix(), missing, null))
				.andExpect(status().isConflict())
				.andExpect(content().string(missing + " can't be lent: it is missing and not on loan to this patient"));
		mvc.perform(homeLoan(staff, patient.suffix(), othersChair, null))
				.andExpect(status().isConflict());
		mvc.perform(homeLoan(staff, patient.suffix(), free, today().minusDays(1)))
				.andExpect(status().isBadRequest())
				.andExpect(content().string("The return date can't be in the past"));
		mvc.perform(withToken(post("/api/home-loans"), staff).contentType(MediaType.APPLICATION_JSON)
				.content("{\"mrn\":\"" + patient.suffix() + "\",\"items\":[]}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().string("Choose at least one item to lend"));

		long id = requestHomeLoan(staff, patient.suffix(), free, null, null, "requested");
		mvc.perform(homeLoan(staff, patient.suffix(), free, null))
				.andExpect(status().isConflict())
				.andExpect(content().string("A home loan for this patient is already waiting for a manager"));
		mvc.perform(withToken(get("/api/home-loans/pending"), staff)).andExpect(status().isForbidden());
		mvc.perform(jsonBody(withToken(post("/api/home-loans/" + id + "/approve"), staff), "reason", "x")).andExpect(status().isForbidden());

		// The item was issued elsewhere before the manager got to it: approval fails, request stays open
		jdbc.update("update equipment set status = 'issued' where label_code = ?", free);
		mvc.perform(jsonBody(withToken(post("/api/home-loans/" + id + "/approve"), tokenFor(Role.MANAGER)), "reason", "OK"))
				.andExpect(status().isConflict());
		assertThat(jdbc.queryForObject("select status from home_loan_request where id = ?", String.class, id)).isEqualTo("requested");
	}

	@Test
	void homeLoanLocationIsNotOfferedAsAPlace() throws Exception {
		String body = mvc.perform(withToken(get("/api/locations"), tokenFor(Role.MANAGER))).andReturn().getResponse().getContentAsString();
		assertThat((List<String>) JsonPath.read(body, "$[*].name")).doesNotContain("Home loan").contains("OT store");
	}

	private long requestHomeLoan(String token, String mrn, String label, LocalDate due, String note, String expectedStatus) throws Exception {
		String body = mvc.perform(homeLoan(token, mrn, label, due, note))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value(expectedStatus))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.id")).longValue();
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder homeLoan(String token, String mrn, String label, LocalDate due) {
		return homeLoan(token, mrn, label, due, null);
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder homeLoan(String token, String mrn, String label,
			LocalDate due, String note) {
		String item = "{\"equipmentId\":\"" + label + "\"" + (due == null ? "" : ",\"dueOn\":\"" + due + "\"") + "}";
		String noteJson = note == null ? "" : ",\"note\":\"" + note + "\"";
		return withToken(post("/api/home-loans"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"mrn\":\"" + mrn + "\",\"items\":[" + item + "]" + noteJson + "}");
	}

	private static String day(LocalDate date) {
		return date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH));
	}

}
