package nhs.ot.equiptrack;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import nhs.ot.equiptrack.discharge.MrnHasher;
import nhs.ot.equiptrack.user.AppUser;
import nhs.ot.equiptrack.user.Role;
import nhs.ot.equiptrack.user.UserRepository;

// Helpers for API tests. Each test creates its own users, equipment types and
// items with unique names, so tests never depend on each other's data.
@IntegrationTest
public abstract class ApiTestSupport {

	protected static final String PASSWORD = "correct-horse-1";
	protected static final ZoneId ZONE = ZoneId.of("Europe/Dublin");

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected UserRepository users;

	@Autowired
	protected PasswordEncoder encoder;

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected MrnHasher mrnHasher;

	// ------------------------------------------------------------------ users

	protected AppUser createUser(Role role) {
		String username = role.name().toLowerCase() + "-" + unique();
		return users.save(new AppUser(username, encoder.encode(PASSWORD), "Test " + username, "OT equipment team", role));
	}

	protected String login(String username, String password) throws Exception {
		String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(json("username", username, "password", password)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.token");
	}

	protected String tokenFor(Role role) throws Exception {
		return login(createUser(role).getUsername(), PASSWORD);
	}

	protected String tokenFor(AppUser user) throws Exception {
		return login(user.getUsername(), PASSWORD);
	}

	// --------------------------------------------------------------- fixtures

	// A fresh category with one type, so counts per type and category are this test's alone
	protected record TypeRef(long id, String name, String category) {
	}

	protected TypeRef newType() {
		String suffix = unique();
		String category = "Category " + suffix;
		String type = "Type " + suffix;
		long categoryId = jdbc.queryForObject(
				"insert into equipment_category (name, icon_key, sort_order) values (?, 'layers', 100) returning id", Long.class, category);
		long typeId = jdbc.queryForObject(
				"insert into equipment_type (name, category_id, default_loan_days, label_prefix) values (?, ?, 14, ?) returning id", Long.class, type, categoryId,
				"T" + suffix.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "0"));
		return new TypeRef(typeId, type, category);
	}

	protected String newEquipment(TypeRef type, String status, String location) {
		return newEquipment(type, status, location, Duration.ZERO);
	}

	protected String newEquipment(TypeRef type, String status, String location, Duration lastSeenAgo) {
		String label = "T-" + unique().toUpperCase(Locale.ROOT);
		jdbc.update("""
				insert into equipment (label_code, name, type_id, pool_id, status, location_id, last_seen_at, status_changed_at)
				values (?, ?, ?, (select id from equipment_pool where code = 'MAIN'), ?, (select id from location where name = ?), ?, now())
				""", label, "Test " + type.name(), type.id(), status, location, Timestamp.from(Instant.now().minus(lastSeenAgo)));
		return label;
	}

	// An issued item with its open loan
	protected String issuedEquipment(TypeRef type, String ward, Long patientId, LocalDate dueOn) {
		String label = newEquipment(type, "issued", ward);
		jdbc.update("""
				insert into loan (equipment_id, patient_id, ward_id, issued_at, due_at)
				values ((select id from equipment where label_code = ?), ?, (select id from location where name = ?), now() - interval '2 days', ?)
				""", label, patientId, ward, Timestamp.from(dueOn.atTime(23, 59, 59).atZone(ZONE).toInstant()));
		return label;
	}

	protected long newRequest(String ward, TypeRef type, Duration age) {
		return newRequest(ward, type, age, null);
	}

	protected long newRequest(String ward, TypeRef type, Duration age, Long patientId) {
		return jdbc.queryForObject("""
				insert into waitlist_request (ward_id, type_id, patient_id, requested_at)
				values ((select id from location where name = ?), ?, ?, ?) returning id
				""", Long.class, ward, type.id(), patientId, Timestamp.from(Instant.now().minus(age)));
	}

	protected record PatientRef(long id, String fullMrn, String suffix) {
	}

	// A fictional patient whose last four MRN digits no other patient has
	protected PatientRef newPatient(String ward, LocalDate dischargePlannedOn) {
		String suffix;
		do {
			suffix = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
		} while (jdbc.queryForObject("select count(*) from patient where mrn_suffix = ?", Integer.class, suffix) > 0);
		return newPatientWithSuffix(suffix, ward, dischargePlannedOn);
	}

	protected PatientRef newPatientWithSuffix(String suffix, String ward, LocalDate dischargePlannedOn) {
		String fullMrn = "8" + String.format("%05d", ThreadLocalRandom.current().nextInt(100000)) + suffix;
		long id = jdbc.queryForObject("""
				insert into patient (mrn_hash, mrn_suffix, ward_id, bed, discharge_planned_on)
				values (?, ?, (select id from location where name = ?), '12', ?) returning id
				""", Long.class, mrnHasher.hash(fullMrn), suffix, ward, dischargePlannedOn);
		return new PatientRef(id, fullMrn, suffix);
	}

	protected String statusOf(String label) {
		return jdbc.queryForObject("select status from equipment where label_code = ?", String.class, label);
	}

	protected LocalDate today() {
		return LocalDate.now(ZONE);
	}

	protected static String unique() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	// ---------------------------------------------------------------- requests

	protected static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String token) {
		return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
	}

	protected static MockHttpServletRequestBuilder jsonBody(MockHttpServletRequestBuilder request, Object... pairs) {
		return request.contentType(MediaType.APPLICATION_JSON).content(json(pairs));
	}

	// json("a", 1, "b", "x") -> {"a":1,"b":"x"}
	protected static String json(Object... pairs) {
		StringBuilder out = new StringBuilder("{");
		for (int i = 0; i < pairs.length; i += 2) {
			if (i > 0) {
				out.append(',');
			}
			out.append('"').append(pairs[i]).append("\":");
			Object value = pairs[i + 1];
			out.append(value instanceof String s ? '"' + s.replace("\"", "\\\"") + '"' : String.valueOf(value));
		}
		return out.append('}').toString();
	}

}
