package nhs.ot.equiptrack.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import nhs.ot.equiptrack.ApiTestSupport;
import nhs.ot.equiptrack.user.AppUser;
import nhs.ot.equiptrack.user.Role;

class AuditWalkFlowIntegrationTest extends ApiTestSupport {

	// The walk is shared by the whole team, so each test starts with none in progress
	@BeforeEach
	void noWalkInProgress() {
		jdbc.update("update audit_walk set status = 'closed', closed_at = now() where status = 'open'");
	}

	@Test
	void startScanAndFinish() throws Exception {
		AppUser user = createUser(Role.OT_STAFF);
		String token = tokenFor(user);
		String ward = newWard();
		TypeRef type = newType();
		String present = newEquipment(type, "available", ward);
		String lost = newEquipment(type, "available", ward);
		String wasMissing = newEquipment(type, "missing", ward);

		mvc.perform(withToken(post("/audit"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"locations\":[\"" + ward + "\"]}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.area").value(ward))
				.andExpect(jsonPath("$.by").value(user.getDisplayName()))
				.andExpect(jsonPath("$.total").value(3))
				.andExpect(jsonPath("$.found").value(0));

		mvc.perform(jsonBody(withToken(post("/audit/scan"), token), "equipmentId", present.toLowerCase()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.found").value(1))
				.andExpect(jsonPath("$.missing.length()").value(2));
		// Scanning twice counts once
		mvc.perform(jsonBody(withToken(post("/audit/scan"), token), "equipmentId", present))
				.andExpect(jsonPath("$.found").value(1));
		// A missing item that turns up goes to inspection before it is used again
		mvc.perform(jsonBody(withToken(post("/audit/scan"), token), "equipmentId", wasMissing))
				.andExpect(jsonPath("$.found").value(2))
				.andExpect(jsonPath("$.missing[0].id").value(lost));
		assertThat(statusOf(wasMissing)).isEqualTo("inspect");

		mvc.perform(withToken(post("/audit/finish"), token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(3))
				.andExpect(jsonPath("$.found").value(2))
				.andExpect(jsonPath("$.markedMissing").value(1));
		assertThat(statusOf(lost)).isEqualTo("missing");
		assertThat(statusOf(present)).isEqualTo("available");
		assertThat(jdbc.queryForList("select title from activity_log where user_id = ? order by id", String.class, user.getId()))
				.containsExactly("Audit walk started", "Test " + type.name() + " checked", "Test " + type.name() + " found",
						"Test " + type.name() + " reported missing", "Audit walk finished");

		mvc.perform(withToken(get("/audit"), token)).andExpect(status().isNotFound());
	}

	@Test
	void onlyOneWalkAtATime() throws Exception {
		String token = tokenFor(Role.OT_STAFF);
		mvc.perform(withToken(post("/audit"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"locations\":[\"OT store\"],\"area\":\"Store check\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.area").value("Store check"));
		mvc.perform(withToken(post("/audit"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"locations\":[\"Ward 3A\"]}"))
				.andExpect(status().isConflict())
				.andExpect(content().string("An audit walk is already in progress. Finish it before starting another."));
	}

	@Test
	void errors() throws Exception {
		String token = tokenFor(Role.OT_STAFF);
		mvc.perform(jsonBody(withToken(post("/audit/scan"), token), "equipmentId", "CU-009"))
				.andExpect(status().isConflict())
				.andExpect(content().string("No audit walk is in progress. Start one first."));
		mvc.perform(withToken(post("/audit/finish"), token))
				.andExpect(status().isConflict());
		mvc.perform(withToken(post("/audit"), token).contentType(MediaType.APPLICATION_JSON).content("{\"locations\":[]}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().string("Choose at least one location"));
		mvc.perform(withToken(post("/audit"), token).contentType(MediaType.APPLICATION_JSON).content("{\"locations\":[\"Mars\"]}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().string("Unknown location Mars"));

		mvc.perform(withToken(post("/audit"), token).contentType(MediaType.APPLICATION_JSON).content("{\"locations\":[\"Workshop\"]}"))
				.andExpect(status().isCreated());
		mvc.perform(jsonBody(withToken(post("/audit/scan"), token), "equipmentId", "NOPE-9"))
				.andExpect(status().isNotFound());
	}

	// A ward of its own, so the walk expects only this test's items
	private String newWard() {
		String name = "Ward T" + unique();
		jdbc.update("insert into location (name, kind) values (?, 'ward')", name);
		return name;
	}

}
