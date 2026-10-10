package nhs.ot.equiptrack.waitlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

import nhs.ot.equiptrack.ApiTestSupport;
import nhs.ot.equiptrack.user.AppUser;
import nhs.ot.equiptrack.user.Role;

class WaitlistIntegrationTest extends ApiTestSupport {

	@Test
	void listsOldestFirstAndMarksWhatCanBeAllocatedNow() throws Exception {
		TypeRef type = newType();
		long newest = newRequest("Ward 6A", type, Duration.ofMinutes(1));
		long oldest = newRequest("Ward 3A", type, Duration.ofDays(6));
		long middle = newRequest("Ward 4A", type, Duration.ofDays(3));
		newEquipment(type, "available", "OT store");
		newEquipment(type, "available", "OT store");
		newEquipment(type, "issued", "Ward 2B"); // not available, doesn't count

		List<Map<String, Object>> mine = entriesFor(type);
		assertThat(mine).extracting(e -> ((Number) e.get("id")).longValue()).containsExactly(oldest, middle, newest);
		assertThat(mine).extracting(e -> e.get("ward")).containsExactly("Ward 3A", "Ward 4A", "Ward 6A");
		assertThat(mine).extracting(e -> e.get("days")).containsExactly(6, 3, 0);
		assertThat(mine).extracting(e -> e.get("availableNow")).containsExactly(true, true, false);
	}

	@Test
	void allocateIssuesItemToTheRequestingWard() throws Exception {
		AppUser user = createUser(Role.OT_STAFF);
		TypeRef type = newType();
		PatientRef patient = newPatient("Ward 3A", null);
		long request = newRequest("Ward 3A", type, Duration.ofDays(2), patient.id());
		String label = newEquipment(type, "available", "OT store");

		mvc.perform(jsonBody(withToken(post("/api/waitlist/" + request + "/allocate"), tokenFor(user)), "equipmentId", label.toLowerCase()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(label))
				.andExpect(jsonPath("$.status").value("issued"))
				.andExpect(jsonPath("$.location").value("Ward 3A"))
				.andExpect(jsonPath("$.note").value("Loaned today · Due " + day(today().plusDays(14))));

		assertThat(entriesFor(type)).isEmpty();
		Map<String, Object> loan = jdbc.queryForMap("""
				select l.patient_id, l.issued_by, l.waitlist_request_id, w.status, w.allocated_equipment_id = l.equipment_id as linked
				from loan l join waitlist_request w on w.id = l.waitlist_request_id
				where l.equipment_id = (select id from equipment where label_code = ?)
				""", label);
		assertThat(loan).containsEntry("patient_id", patient.id()).containsEntry("issued_by", user.getId())
				.containsEntry("status", "allocated").containsEntry("linked", true);
		assertThat(jdbc.queryForObject("select detail from activity_log where equipment_id = (select id from equipment where label_code = ?)",
				String.class, label)).isEqualTo(label + " · Ward 3A");
	}

	@Test
	void staffCanSetTheDueDate() throws Exception {
		TypeRef type = newType();
		long request = newRequest("Ward 3A", type, Duration.ofDays(1));
		String label = newEquipment(type, "available", "OT store");
		LocalDate due = today().plusDays(3);

		mvc.perform(jsonBody(withToken(post("/api/waitlist/" + request + "/allocate"), tokenFor(Role.OT_STAFF)),
				"equipmentId", label, "dueOn", due.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.note").value("Loaned today · Due " + day(due)));
	}

	@Test
	void allocateErrors() throws Exception {
		String token = tokenFor(Role.OT_STAFF);
		TypeRef type = newType();
		long request = newRequest("Ward 3A", type, Duration.ofDays(1));
		String available = newEquipment(type, "available", "OT store");
		String issued = newEquipment(type, "issued", "Ward 2B");
		String otherType = newEquipment(newType(), "available", "OT store");

		mvc.perform(jsonBody(withToken(post("/api/waitlist/999999/allocate"), token), "equipmentId", available))
				.andExpect(status().isNotFound()).andExpect(content().string("Request not found"));
		mvc.perform(jsonBody(withToken(post("/api/waitlist/" + request + "/allocate"), token), "equipmentId", "NOPE-4"))
				.andExpect(status().isNotFound()).andExpect(content().string("No equipment with ID NOPE-4"));
		mvc.perform(jsonBody(withToken(post("/api/waitlist/" + request + "/allocate"), token), "equipmentId", issued))
				.andExpect(status().isConflict()).andExpect(content().string(issued + " can't be allocated to this request"));
		mvc.perform(jsonBody(withToken(post("/api/waitlist/" + request + "/allocate"), token), "equipmentId", otherType))
				.andExpect(status().isConflict()).andExpect(content().string(otherType + " can't be allocated to this request"));
		mvc.perform(jsonBody(withToken(post("/api/waitlist/" + request + "/allocate"), token)))
				.andExpect(status().isBadRequest()).andExpect(content().string("Choose the item to allocate"));
		mvc.perform(jsonBody(withToken(post("/api/waitlist/" + request + "/allocate"), token),
				"equipmentId", available, "dueOn", today().minusDays(1).toString()))
				.andExpect(status().isBadRequest()).andExpect(content().string("The due date can't be in the past"));
		mvc.perform(jsonBody(withToken(post("/api/waitlist/abc/allocate"), token), "equipmentId", available))
				.andExpect(status().isBadRequest());

		// Nothing above changed anything
		assertThat(statusOf(available)).isEqualTo("available");
		assertThat(entriesFor(type)).hasSize(1);

		mvc.perform(jsonBody(withToken(post("/api/waitlist/" + request + "/allocate"), token), "equipmentId", available))
				.andExpect(status().isOk());
		mvc.perform(jsonBody(withToken(post("/api/waitlist/" + request + "/allocate"), token), "equipmentId", newEquipment(type, "available", "OT store")))
				.andExpect(status().isConflict()).andExpect(content().string("This request has already been allocated"));
	}

	private List<Map<String, Object>> entriesFor(TypeRef type) throws Exception {
		String body = mvc.perform(withToken(get("/api/waitlist"), tokenFor(Role.OT_STAFF)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$[?(@.type == '" + type.name() + "')]");
	}

	private static String day(LocalDate date) {
		return date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH));
	}

}
