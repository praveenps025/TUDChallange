package nhs.ot.equiptrack.audit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;

import nhs.ot.equiptrack.ApiTestSupport;
import nhs.ot.equiptrack.user.AppUser;
import nhs.ot.equiptrack.user.Role;

class AuditIntegrationTest extends ApiTestSupport {

	@Test
	void currentWalkShowsProgressAndWhatIsStillToFind() throws Exception {
		AppUser starter = createUser(Role.OT_STAFF);
		TypeRef type = newType();
		String foundA = newEquipment(type, "available", "OT store");
		String foundB = newEquipment(type, "available", "OT store");
		String notYet = newEquipment(type, "missing", "Ward 3B");
		String notExpected = newEquipment(type, "available", "OT store");

		long walk = jdbc.queryForObject("""
				insert into audit_walk (area, started_by, started_at) values ('Test area', ?, now()) returning id
				""", Long.class, starter.getId());
		for (String label : new String[] { foundA, foundB, notYet }) {
			jdbc.update("""
					insert into audit_walk_item (audit_walk_id, equipment_id, expected_location_id)
					select ?, id, location_id from equipment where label_code = ?
					""", walk, label);
		}
		for (String label : new String[] { foundA, foundB, notExpected }) {
			jdbc.update("insert into audit_scan (audit_walk_id, equipment_id) select ?, id from equipment where label_code = ?", walk, label);
		}
		String started = jdbc.queryForObject("select started_at from audit_walk where id = ?", java.sql.Timestamp.class, walk)
				.toInstant().atZone(ZONE).format(DateTimeFormatter.ofPattern("HH:mm"));

		mvc.perform(withToken(get("/audit"), tokenFor(Role.OT_STAFF)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.area").value("Test area"))
				.andExpect(jsonPath("$.started").value(started))
				.andExpect(jsonPath("$.by").value(starter.getDisplayName()))
				.andExpect(jsonPath("$.total").value(3))
				.andExpect(jsonPath("$.found").value(2))
				.andExpect(jsonPath("$.missing.length()").value(1))
				.andExpect(jsonPath("$.missing[0].id").value(notYet))
				.andExpect(jsonPath("$.missing[0].location").value("Ward 3B"));
	}

	@Test
	void noWalkInProgressIs404() throws Exception {
		jdbc.update("update audit_walk set status = 'closed', closed_at = now() where status = 'open'");
		mvc.perform(withToken(get("/audit"), tokenFor(Role.OT_STAFF)))
				.andExpect(status().isNotFound())
				.andExpect(content().string("No audit walk is in progress"));
	}

}
