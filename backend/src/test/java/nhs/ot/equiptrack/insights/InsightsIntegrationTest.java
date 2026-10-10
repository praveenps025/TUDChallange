package nhs.ot.equiptrack.insights;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

import nhs.ot.equiptrack.ApiTestSupport;
import nhs.ot.equiptrack.user.Role;

class InsightsIntegrationTest extends ApiTestSupport {

	@Test
	void countsByCategoryWithAdvice() throws Exception {
		TypeRef shortOfStock = newType();
		newEquipment(shortOfStock, "available", "OT store");
		newEquipment(shortOfStock, "issued", "Ward 2B");
		newEquipment(shortOfStock, "inspect", "OT store");
		newEquipment(shortOfStock, "missing", "Ward 3B");
		newRequest("Ward 3A", shortOfStock, Duration.ofDays(2));
		newRequest("Ward 4A", shortOfStock, Duration.ofDays(1));

		TypeRef enough = newType();
		newEquipment(enough, "available", "OT store");
		newEquipment(enough, "available", "OT store");
		newRequest("Ward 3A", enough, Duration.ofDays(2));

		TypeRef quiet = newType();
		newEquipment(quiet, "available", "OT store");

		Map<String, Object> overview = overview();
		assertThat(category(overview, shortOfStock)).containsAllEntriesOf(Map.of(
				"available", 1, "issued", 1, "check", 2, "waiting", 2, "advice", "Consider buying"));
		assertThat(category(overview, enough)).containsAllEntriesOf(Map.of(
				"available", 2, "issued", 0, "check", 0, "waiting", 1, "advice", "Redistribute"));
		assertThat(category(overview, quiet)).containsEntry("waiting", 0).containsEntry("advice", null);
	}

	@Test
	void headlineFiguresComeFromTheDatabase() throws Exception {
		Map<String, Object> before = overview();
		TypeRef type = newType();
		newEquipment(type, "missing", "Ward 3B");
		newEquipment(type, "available", "OT store");
		newRequest("Ward 3A", type, Duration.ofDays(1));
		Map<String, Object> after = overview();

		assertThat(after.get("month")).isEqualTo(today().getMonth().getDisplayName(TextStyle.FULL, Locale.UK));
		assertThat(delta(before, after, "tracked")).isEqualTo(2);
		assertThat(delta(before, after, "unaccounted")).isEqualTo(1);
		assertThat(delta(before, after, "waitlists")).isEqualTo(1);
		assertThat(delta(before, after, "readyToAllocate")).isEqualTo(1);
	}

	@Test
	void onTimeIsTheShareOfThisMonthsReturnsMadeByTheDueDate() throws Exception {
		TypeRef type = newType();
		String onTime = issuedEquipment(type, "Ward 2B", null, today().plusDays(2));
		String late = issuedEquipment(type, "Ward 2B", null, today().minusDays(1));
		String token = tokenFor(Role.MANAGER);
		for (String label : new String[] { onTime, late }) {
			mvc.perform(jsonBody(withToken(post("/api/equipment/" + label + "/return"), token), "clean", true)).andExpect(status().isOk());
		}

		Map<String, Object> monthStart = jdbc.queryForMap("""
				select count(*) as total, count(*) filter (where returned_at <= due_at) as on_time from loan
				where returned_at >= date_trunc('month', now() at time zone 'Europe/Dublin') at time zone 'Europe/Dublin'
				""");
		long total = ((Number) monthStart.get("total")).longValue();
		long inTime = ((Number) monthStart.get("on_time")).longValue();
		assertThat(overview().get("onTime")).isEqualTo((int) Math.round(100.0 * inTime / total));
	}

	private Map<String, Object> overview() throws Exception {
		String body = mvc.perform(withToken(get("/api/insights"), tokenFor(Role.MANAGER)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> category(Map<String, Object> overview, TypeRef type) {
		return ((List<Map<String, Object>>) overview.get("categories")).stream()
				.filter(c -> c.get("name").equals(type.category())).findFirst().orElseThrow();
	}

	private static long delta(Map<String, Object> before, Map<String, Object> after, String key) {
		return ((Number) after.get(key)).longValue() - ((Number) before.get(key)).longValue();
	}

}
