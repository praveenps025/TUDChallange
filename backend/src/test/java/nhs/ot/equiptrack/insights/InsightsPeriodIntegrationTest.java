package nhs.ot.equiptrack.insights;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

import nhs.ot.equiptrack.ApiTestSupport;
import nhs.ot.equiptrack.user.Role;

class InsightsPeriodIntegrationTest extends ApiTestSupport {

	@Test
	void lastMonthCountsOnlyLastMonthsLoans() throws Exception {
		String token = tokenFor(Role.MANAGER);
		LocalDate lastMonth = today().withDayOfMonth(1).minusMonths(1).plusDays(3);
		Map<String, Object> before = overview(token, "last-month");

		TypeRef type = newType();
		String onTime = newEquipment(type, "available", "OT store");
		String late = newEquipment(type, "available", "OT store");
		loan(onTime, lastMonth, lastMonth.plusDays(5), lastMonth.plusDays(4)); // returned before due
		loan(late, lastMonth, lastMonth.plusDays(2), lastMonth.plusDays(6));   // returned after due

		Map<String, Object> after = overview(token, "last-month");
		assertThat(after.get("period")).isEqualTo("last-month");
		assertThat(after.get("month")).isEqualTo(lastMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.UK));
		assertThat(number(after, "issued") - number(before, "issued")).isEqualTo(2);
		assertThat(number(after, "returned") - number(before, "returned")).isEqualTo(2);

		// This month's figures are unaffected by last month's loans
		Map<String, Object> thisMonth = overview(token, "this-month");
		assertThat(thisMonth.get("month")).isEqualTo(today().getMonth().getDisplayName(TextStyle.FULL, Locale.UK));
	}

	@Test
	void thisYearIsLabelledWithTheYear() throws Exception {
		mvc.perform(withToken(get("/insights").param("period", "this-year"), tokenFor(Role.MANAGER)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.period").value("this-year"))
				.andExpect(jsonPath("$.month").value(String.valueOf(today().getYear())));
	}

	@Test
	void defaultsToThisMonthAndRejectsUnknownPeriods() throws Exception {
		String token = tokenFor(Role.MANAGER);
		mvc.perform(withToken(get("/insights"), token))
				.andExpect(jsonPath("$.period").value("this-month"));
		mvc.perform(withToken(get("/insights").param("period", "last-decade"), token))
				.andExpect(status().isBadRequest())
				.andExpect(content().string("Period must be this-month, last-month or this-year"));
	}

	private void loan(String label, LocalDate issued, LocalDate due, LocalDate returned) {
		jdbc.update("""
				insert into loan (equipment_id, ward_id, issued_at, due_at, returned_at, returned_clean)
				values ((select id from equipment where label_code = ?), (select id from location where name = 'Ward 2B'), ?, ?, ?, true)
				""", label, at(issued, 10), at(due, 23), at(returned, 12));
	}

	private static Timestamp at(LocalDate date, int hour) {
		return Timestamp.from(date.atTime(hour, 0).atZone(ZONE).toInstant());
	}

	private Map<String, Object> overview(String token, String period) throws Exception {
		String body = mvc.perform(withToken(get("/insights").param("period", period), token))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$");
	}

	private static long number(Map<String, Object> map, String key) {
		return ((Number) map.get(key)).longValue();
	}

}
