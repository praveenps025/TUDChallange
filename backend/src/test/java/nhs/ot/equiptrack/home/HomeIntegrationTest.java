package nhs.ot.equiptrack.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

import nhs.ot.equiptrack.ApiTestSupport;
import nhs.ot.equiptrack.user.AppUser;
import nhs.ot.equiptrack.user.Role;

class HomeIntegrationTest extends ApiTestSupport {

	// Home shows at most five tasks of each kind, so start from a quiet
	// database: no loans out, nobody due to leave, no approvals waiting
	@BeforeEach
	void quietDatabase() {
		jdbc.update("update loan set returned_at = now(), returned_clean = true where returned_at is null");
		jdbc.update("update patient set discharge_planned_on = null");
		jdbc.update("update discharge_override set status = 'rejected' where status = 'requested'");
	}

	@Test
	void greetsTheSignedInUser() throws Exception {
		AppUser user = createUser(Role.OT_STAFF);
		mvc.perform(withToken(get("/api/home"), tokenFor(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.user").value("Test"))
				.andExpect(jsonPath("$.team").value("OT equipment team"));
	}

	@Test
	void suggestsAllocatingAnAvailableItemToTheOldestRequest() throws Exception {
		TypeRef type = newType();
		newRequest("Ward 6A", type, Duration.ofDays(1));
		newRequest("Ward 3A", type, Duration.ofDays(4));
		String item = newEquipment(type, "available", "OT store");

		assertThat(tasks()).contains(Map.of("icon", "layers", "title", "Allocate " + type.name().toLowerCase(),
				"detail", item + " · Ward 3A is next", "to", "/waitlist?allocate=" + item));
	}

	@Test
	void listsLoansDueTodayAndOverdue() throws Exception {
		TypeRef type = newType();
		String dueToday = issuedEquipment(type, "Ward 5A", null, today());
		String overdue = issuedEquipment(type, "Ward 2B", null, today().minusDays(30));
		String later = issuedEquipment(type, "Ward 2B", null, today().plusDays(5));

		List<Map<String, String>> tasks = tasks();
		assertThat(tasks).contains(Map.of("icon", "clock", "title", "Loan due",
				"detail", dueToday + " · Ward 5A · Due today", "to", "/item/" + dueToday));
		assertThat(tasks).anyMatch(t -> t.get("title").equals("Loan overdue") && t.get("detail").startsWith(overdue + " · Ward 2B · Overdue since "));
		assertThat(tasks).noneMatch(t -> t.get("detail").startsWith(later));
	}

	@Test
	void countsItemsNotSeenForAWeek() throws Exception {
		newEquipment(newType(), "available", "OT store", Duration.ofDays(10));
		assertThat(tasks()).anyMatch(t -> t.get("title").equals("Unseen items") && t.get("to").equals("/audit")
				&& t.get("detail").endsWith("Last seen over 7 days ago"));
	}

	@Test
	void flagsPatientsLeavingWithEquipmentOut() throws Exception {
		PatientRef leaving = newPatient("Ward 4C", today());
		issuedEquipment(newType(), "Ward 4C", leaving.id(), today().plusDays(3));
		PatientRef staying = newPatient("Ward 4C", today().plusDays(5));
		issuedEquipment(newType(), "Ward 4C", staying.id(), today().plusDays(3));

		List<Map<String, String>> tasks = tasks();
		assertThat(tasks).contains(Map.of("icon", "clipboard", "title", "Discharge check",
				"detail", "Ward 4C · MRN ••••" + leaving.suffix(), "to", "/discharge?mrn=" + leaving.suffix()));
		assertThat(tasks).noneMatch(t -> t.get("detail").endsWith("••••" + staying.suffix()));
	}

	@Test
	void showsLatestActivityWithTime() throws Exception {
		AppUser user = createUser(Role.OT_STAFF);
		String token = tokenFor(user);
		String label = newEquipment(newType(), "inspect", "OT store");
		mvc.perform(withToken(post("/api/equipment/" + label + "/inspect"), token)).andExpect(status().isOk());

		String body = mvc.perform(withToken(get("/api/home"), token)).andReturn().getResponse().getContentAsString();
		Map<String, String> latest = JsonPath.read(body, "$.activity[0]");
		assertThat(latest.get("icon")).isEqualTo("clipboard");
		assertThat(latest.get("detail")).isEqualTo(label + " · Available");
		assertThat(latest.get("time")).matches("\\d\\d:\\d\\d");
		assertThat((List<?>) JsonPath.read(body, "$.activity")).hasSizeLessThanOrEqualTo(5);
	}

	private List<Map<String, String>> tasks() throws Exception {
		String body = mvc.perform(withToken(get("/api/home"), tokenFor(Role.OT_STAFF)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.tasks");
	}

}
