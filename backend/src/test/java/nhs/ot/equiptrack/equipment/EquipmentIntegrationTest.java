package nhs.ot.equiptrack.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import nhs.ot.equiptrack.ApiTestSupport;
import nhs.ot.equiptrack.common.ApiExceptionHandler;
import nhs.ot.equiptrack.user.AppUser;
import nhs.ot.equiptrack.user.Role;

class EquipmentIntegrationTest extends ApiTestSupport {

	@Test
	void getsItemByLabelIgnoringCase() throws Exception {
		TypeRef type = newType();
		String label = issuedEquipment(type, "Ward 2B", null, today());

		mvc.perform(withToken(get("/equipment/" + label.toLowerCase()), tokenFor(Role.OT_STAFF)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(label))
				.andExpect(jsonPath("$.name").value("Test " + type.name()))
				.andExpect(jsonPath("$.type").value(type.name()))
				.andExpect(jsonPath("$.category").value(type.category()))
				.andExpect(jsonPath("$.status").value("issued"))
				.andExpect(jsonPath("$.location").value("Ward 2B"))
				.andExpect(jsonPath("$.note").value("Loaned " + dayLabel(2) + " · Due today"));
	}

	@Test
	void unknownItemIs404() throws Exception {
		mvc.perform(withToken(get("/equipment/NOPE-1"), tokenFor(Role.OT_STAFF)))
				.andExpect(status().isNotFound())
				.andExpect(content().string("No equipment with ID NOPE-1"));
	}

	@Test
	void requiresSignIn() throws Exception {
		mvc.perform(get("/equipment/CU-024")).andExpect(status().isUnauthorized());
		mvc.perform(post("/equipment/CU-024/inspect")).andExpect(status().isUnauthorized());
	}

	@Test
	void cleanReturnGoesBackToStock() throws Exception {
		AppUser user = createUser(Role.OT_STAFF);
		String label = issuedEquipment(newType(), "Ward 5A", null, today().plusDays(3));

		mvc.perform(jsonBody(withToken(post("/equipment/" + label + "/return"), tokenFor(user)), "clean", true))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("available"))
				.andExpect(jsonPath("$.location").value("OT store"))
				.andExpect(jsonPath("$.note").value("Checked today"));

		assertThat(jdbc.queryForMap("select returned_at is not null as closed, returned_clean, returned_by from loan "
				+ "where equipment_id = (select id from equipment where label_code = ?)", label))
				.containsEntry("closed", true).containsEntry("returned_clean", true).containsEntry("returned_by", user.getId());
		assertThat(activityFor(label)).containsExactly("in|Test " + typeNameOf(label) + " returned|" + label + " · OT store|" + user.getId());
	}

	@Test
	void dirtyReturnNeedsInspection() throws Exception {
		String label = issuedEquipment(newType(), "Ward 5A", null, today());
		mvc.perform(jsonBody(withToken(post("/equipment/" + label + "/return"), tokenFor(Role.OT_STAFF)), "clean", false))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("inspect"))
				.andExpect(jsonPath("$.note").value("Returned today · Needs inspection"));
	}

	@Test
	void returnErrors() throws Exception {
		String token = tokenFor(Role.OT_STAFF);
		String available = newEquipment(newType(), "available", "OT store");

		mvc.perform(jsonBody(withToken(post("/equipment/" + available + "/return"), token), "clean", true))
				.andExpect(status().isConflict())
				.andExpect(content().string(available + " is not issued"));
		mvc.perform(jsonBody(withToken(post("/equipment/" + available + "/return"), token)))
				.andExpect(status().isBadRequest())
				.andExpect(content().string("Say whether the item is clean and undamaged"));
		mvc.perform(jsonBody(withToken(post("/equipment/NOPE-2/return"), token), "clean", true))
				.andExpect(status().isNotFound());
		assertThat(statusOf(available)).isEqualTo("available");
	}

	@Test
	void inspectMakesItemAvailable() throws Exception {
		AppUser user = createUser(Role.OT_STAFF);
		String label = newEquipment(newType(), "inspect", "OT store");

		mvc.perform(withToken(post("/equipment/" + label + "/inspect"), tokenFor(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("available"));
		assertThat(activityFor(label)).containsExactly("clipboard|Test " + typeNameOf(label) + " inspected|" + label + " · Available|" + user.getId());
	}

	@Test
	void inspectErrors() throws Exception {
		String token = tokenFor(Role.OT_STAFF);
		String label = newEquipment(newType(), "available", "OT store");
		mvc.perform(withToken(post("/equipment/" + label + "/inspect"), token))
				.andExpect(status().isConflict())
				.andExpect(content().string(label + " doesn't need inspection"));
		mvc.perform(withToken(post("/equipment/NOPE-3/inspect"), token)).andExpect(status().isNotFound());
	}

	// Several people return the same item at the same moment: exactly one wins,
	// the rest get a 409 explaining what happened, and only one change is recorded
	@Test
	void concurrentReturnsOnlySucceedOnce() throws Exception {
		String label = issuedEquipment(newType(), "Ward 2B", null, today());
		List<String> tokens = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			tokens.add(tokenFor(Role.OT_STAFF));
		}

		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(tokens.size());
		List<Future<MockHttpServletResponse>> results = new ArrayList<>();
		for (String token : tokens) {
			Callable<MockHttpServletResponse> call = () -> {
				start.await();
				return mvc.perform(jsonBody(withToken(post("/equipment/" + label + "/return"), token), "clean", true))
						.andReturn().getResponse();
			};
			results.add(pool.submit(call));
		}
		start.countDown();

		int ok = 0;
		for (Future<MockHttpServletResponse> result : results) {
			MockHttpServletResponse response = result.get();
			if (response.getStatus() == 200) {
				ok++;
			} else {
				assertThat(response.getStatus()).isEqualTo(409);
				assertThat(response.getContentAsString()).isIn(label + " is not issued", ApiExceptionHandler.CONCURRENT_UPDATE);
			}
		}
		pool.shutdown();

		assertThat(ok).isEqualTo(1);
		assertThat(activityFor(label)).hasSize(1);
		assertThat(statusOf(label)).isEqualTo("available");
	}

	private List<String> activityFor(String label) {
		return jdbc.queryForList("""
				select kind || '|' || title || '|' || detail || '|' || user_id from activity_log
				where equipment_id = (select id from equipment where label_code = ?) order by id
				""", String.class, label);
	}

	private String typeNameOf(String label) {
		return jdbc.queryForObject("select t.name from equipment e join equipment_type t on t.id = e.type_id where e.label_code = ?",
				String.class, label);
	}

	private String dayLabel(int daysAgo) {
		return switch (daysAgo) {
			case 0 -> "today";
			case 1 -> "yesterday";
			default -> today().minusDays(daysAgo).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH));
		};
	}

}
