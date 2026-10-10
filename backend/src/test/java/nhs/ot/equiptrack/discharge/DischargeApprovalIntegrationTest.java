package nhs.ot.equiptrack.discharge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

import nhs.ot.equiptrack.ApiTestSupport;
import nhs.ot.equiptrack.user.AppUser;
import nhs.ot.equiptrack.user.Role;

class DischargeApprovalIntegrationTest extends ApiTestSupport {

	@Test
	void staffRequestThenManagerApproves() throws Exception {
		AppUser staff = createUser(Role.OT_STAFF);
		AppUser manager = createUser(Role.MANAGER);
		PatientRef patient = newPatient("Ward 4C", today());
		issuedEquipment(newType(), "Ward 4C", patient.id(), today());
		String managerToken = tokenFor(manager);

		long id = requestOverride(tokenFor(staff), patient, "Family will return the chair");

		String pending = mvc.perform(withToken(get("/api/discharge/overrides"), managerToken))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		List<Map<String, Object>> mine = JsonPath.read(pending, "$[?(@.id == " + id + ")]");
		assertThat(mine).singleElement().satisfies(o -> assertThat(o)
				.containsEntry("mrn", patient.suffix()).containsEntry("ward", "Ward 4C")
				.containsEntry("reason", "Family will return the chair").containsEntry("requestedBy", staff.getDisplayName())
				.containsEntry("itemsOut", 1));

		mvc.perform(jsonBody(withToken(post("/api/discharge/overrides/" + id + "/approve"), managerToken), "reason", "Spoke to family"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.override.status").value("approved"))
				.andExpect(jsonPath("$.override.decidedBy").value(manager.getDisplayName()))
				.andExpect(jsonPath("$.override.decisionReason").value("Spoke to family"))
				.andExpect(jsonPath("$.override.requestedBy").value(staff.getDisplayName()));

		String after = mvc.perform(withToken(get("/api/discharge/overrides"), managerToken)).andReturn().getResponse().getContentAsString();
		assertThat((List<?>) JsonPath.read(after, "$[?(@.id == " + id + ")]")).isEmpty();
		mvc.perform(jsonBody(withToken(post("/api/discharge/overrides/" + id + "/reject"), managerToken), "reason", "Too late"))
				.andExpect(status().isConflict())
				.andExpect(content().string("This request has already been approved"));
	}

	@Test
	void managerRejectsWithReasonAndStaffCanAskAgain() throws Exception {
		String staffToken = tokenFor(Role.OT_STAFF);
		PatientRef patient = newPatient("Ward 4C", today());
		issuedEquipment(newType(), "Ward 4C", patient.id(), today());

		long id = requestOverride(staffToken, patient, "Chair is with family");
		mvc.perform(jsonBody(withToken(post("/api/discharge/overrides/" + id + "/reject"), tokenFor(Role.MANAGER)), "reason", "Return the chair first"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.override.status").value("rejected"))
				.andExpect(jsonPath("$.override.decisionReason").value("Return the chair first"));

		requestOverride(staffToken, patient, "Chair is now on the ward");
	}

	@Test
	void managerDoingTheCheckApprovesStraightAway() throws Exception {
		AppUser manager = createUser(Role.MANAGER);
		PatientRef patient = newPatient("Ward 4C", today());
		issuedEquipment(newType(), "Ward 4C", patient.id(), today());

		mvc.perform(jsonBody(withToken(post("/api/discharge/" + patient.suffix() + "/override"), tokenFor(manager)), "reason", "Equipment going home with patient"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.override.status").value("approved"))
				.andExpect(jsonPath("$.override.decidedBy").value(manager.getDisplayName()))
				.andExpect(jsonPath("$.override.decisionReason").value("Equipment going home with patient"));
	}

	@Test
	void onlyManagersApprove() throws Exception {
		String staffToken = tokenFor(Role.OT_STAFF);
		PatientRef patient = newPatient("Ward 4C", today());
		long id = requestOverride(staffToken, patient, "Please");

		mvc.perform(withToken(get("/api/discharge/overrides"), staffToken)).andExpect(status().isForbidden());
		mvc.perform(jsonBody(withToken(post("/api/discharge/overrides/" + id + "/approve"), staffToken), "reason", "Self approve"))
				.andExpect(status().isForbidden());
		mvc.perform(jsonBody(withToken(post("/api/discharge/overrides/" + id + "/approve"), tokenFor(Role.MANAGER)), "reason", " "))
				.andExpect(status().isBadRequest()).andExpect(content().string("A reason is required"));
		mvc.perform(jsonBody(withToken(post("/api/discharge/overrides/999999/approve"), tokenFor(Role.MANAGER)), "reason", "x"))
				.andExpect(status().isNotFound()).andExpect(content().string("Override request not found"));
	}

	@Test
	void managersSeePendingApprovalsOnHome() throws Exception {
		jdbc.update("update discharge_override set status = 'rejected' where status = 'requested'"); // Home shows five at most
		PatientRef patient = newPatient("Ward 4C", null);
		requestOverride(tokenFor(Role.OT_STAFF), patient, "Please");

		String body = mvc.perform(withToken(get("/api/home"), tokenFor(Role.MANAGER))).andReturn().getResponse().getContentAsString();
		List<Map<String, String>> tasks = JsonPath.read(body, "$.tasks");
		assertThat(tasks).anyMatch(t -> t.get("title").equals("Approve discharge") && t.get("to").equals("/discharge?mrn=" + patient.suffix()));

		String staffHome = mvc.perform(withToken(get("/api/home"), tokenFor(Role.OT_STAFF))).andReturn().getResponse().getContentAsString();
		assertThat((List<Map<String, String>>) JsonPath.read(staffHome, "$.tasks")).noneMatch(t -> t.get("title").equals("Approve discharge"));
	}

	private long requestOverride(String token, PatientRef patient, String reason) throws Exception {
		String body = mvc.perform(jsonBody(withToken(post("/api/discharge/" + patient.suffix() + "/override"), token), "reason", reason))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.override.status").value("requested"))
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(body, "$.override.id")).longValue();
	}

}
