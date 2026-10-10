package nhs.ot.equiptrack.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import nhs.ot.equiptrack.ApiTestSupport;
import nhs.ot.equiptrack.user.Role;

class RegistrationIntegrationTest extends ApiTestSupport {

	@Test
	void managerRegistersItemWithTheNextId() throws Exception {
		String token = tokenFor(Role.MANAGER);
		TypeRef type = newType();
		String prefix = prefixOf(type);
		jdbc.update("""
				insert into equipment (label_code, name, type_id, pool_id, status, location_id)
				values (?, 'Old', ?, (select id from equipment_pool where code = 'MAIN'), 'available', (select id from location where name = 'OT store'))
				""", prefix + "-041", type.id());

		mvc.perform(withToken(post("/api/equipment"), token).contentType(MediaType.APPLICATION_JSON).content("""
				{"type": "%s", "pool": "winter", "details": {"name": "Bariatric chair", "model": "BX-500", "serialNumber": "SN-77",
				 "assetNumber": "A-1001", "assetFlag": "AT+", "size": "50 x 45 cm", "maxUserWeight": "250 kg",
				 "hc21Number": "HC-9", "dmlNumber": "DML-4", "comments": "Donated", "purchaseDate": "2026-01-15"}}
				""".formatted(type.name())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(prefix + "-042"))
				.andExpect(jsonPath("$.status").value("available"))
				.andExpect(jsonPath("$.location").value("OT store"))
				.andExpect(jsonPath("$.pool").value("WINTER"))
				.andExpect(jsonPath("$.poolName").value("Winter Initiative"))
				.andExpect(jsonPath("$.serialNumber").value("SN-77"))
				.andExpect(jsonPath("$.assetFlag").value("AT+"))
				.andExpect(jsonPath("$.purchaseDate").value("2026-01-15"))
				.andExpect(jsonPath("$.labelPrintedAt").doesNotExist());

		// Staff can look it up like any other item
		mvc.perform(withToken(get("/api/equipment/" + prefix + "-042"), tokenFor(Role.OT_STAFF)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Bariatric chair"));
	}

	@Test
	void firstItemOfATypeIsNumberOne() throws Exception {
		TypeRef type = newType();
		mvc.perform(jsonRegister(tokenFor(Role.MANAGER), type.name(), "MAIN", "Ward 3A"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(prefixOf(type) + "-001"))
				.andExpect(jsonPath("$.location").value("Ward 3A"));
	}

	@Test
	void staffCannotRegisterEditOrPrint() throws Exception {
		String staff = tokenFor(Role.OT_STAFF);
		TypeRef type = newType();
		mvc.perform(jsonRegister(staff, type.name(), "MAIN", null)).andExpect(status().isForbidden());
		String label = newEquipment(type, "available", "OT store");
		mvc.perform(withToken(put("/api/equipment/" + label), staff).contentType(MediaType.APPLICATION_JSON)
				.content("{\"pool\":\"MAIN\",\"details\":{\"name\":\"x\"}}")).andExpect(status().isForbidden());
		mvc.perform(withToken(post("/api/equipment/" + label + "/label-printed"), staff)).andExpect(status().isForbidden());
		mvc.perform(jsonBody(withToken(post("/api/equipment-types"), staff), "name", "Hoist", "category", "Hoists", "labelPrefix", "HO"))
				.andExpect(status().isForbidden());
	}

	@Test
	void registrationErrors() throws Exception {
		String token = tokenFor(Role.MANAGER);
		TypeRef type = newType();
		mvc.perform(jsonRegister(token, "Teleporter", "MAIN", null))
				.andExpect(status().isBadRequest()).andExpect(content().string("Unknown equipment type Teleporter"));
		mvc.perform(jsonRegister(token, type.name(), "NOPE", null))
				.andExpect(status().isBadRequest()).andExpect(content().string("Unknown funding pool NOPE"));
		mvc.perform(jsonRegister(token, type.name(), "MAIN", "Mars"))
				.andExpect(status().isBadRequest()).andExpect(content().string("Unknown location Mars"));
		mvc.perform(withToken(post("/api/equipment"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"" + type.name() + "\",\"pool\":\"MAIN\",\"details\":{\"name\":\" \"}}"))
				.andExpect(status().isBadRequest()).andExpect(content().string("Enter a name for the item"));
		mvc.perform(withToken(post("/api/equipment"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"" + type.name() + "\",\"pool\":\"MAIN\",\"details\":{\"name\":\"x\",\"assetFlag\":\"AT\"}}"))
				.andExpect(status().isBadRequest()).andExpect(content().string("Asset flag must be AT+ or AT-"));
	}

	@Test
	void managerEditsDetailsAndRecordsLabelPrints() throws Exception {
		String token = tokenFor(Role.MANAGER);
		String label = newEquipment(newType(), "issued", "Ward 2B");

		mvc.perform(withToken(put("/api/equipment/" + label), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"pool\":\"BURKITT\",\"details\":{\"name\":\"Renamed\",\"serialNumber\":\"SN-NEW\"}}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Renamed"))
				.andExpect(jsonPath("$.pool").value("BURKITT"))
				.andExpect(jsonPath("$.status").value("issued")); // editing never changes status

		mvc.perform(withToken(post("/api/equipment/" + label + "/label-printed"), token))
				.andExpect(status().isOk()).andExpect(jsonPath("$.labelPrintedAt").exists());
		mvc.perform(withToken(post("/api/equipment/" + label + "/label-printed"), token)).andExpect(status().isOk());
		assertThat(jdbc.queryForList("select title from activity_log where equipment_id = (select id from equipment where label_code = ?) order by id",
				String.class, label)).containsExactly("Renamed details updated", "Label printed", "Label reprinted");
	}

	@Test
	void managerAddsATypeAndItsCategory() throws Exception {
		String token = tokenFor(Role.MANAGER);
		String suffix = unique().toUpperCase().replaceAll("[^A-Z0-9]", "0");
		String body = mvc.perform(jsonBody(withToken(post("/api/equipment-types"), token),
				"name", "Hoist " + suffix, "category", "Hoists " + suffix, "labelPrefix", "h" + suffix.substring(0, 5), "defaultLoanDays", 7))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.labelPrefix").value("H" + suffix.substring(0, 5)))
				.andExpect(jsonPath("$.defaultLoanDays").value(7))
				.andReturn().getResponse().getContentAsString();
		assertThat((String) JsonPath.read(body, "$.category")).isEqualTo("Hoists " + suffix);

		mvc.perform(jsonBody(withToken(post("/api/equipment-types"), token), "name", "Hoist " + suffix, "category", "x", "labelPrefix", "ZZ" + suffix.substring(0, 4)))
				.andExpect(status().isConflict()).andExpect(content().string("There is already a type called Hoist " + suffix));
		mvc.perform(jsonBody(withToken(post("/api/equipment-types"), token), "name", "Other " + suffix, "category", "x", "labelPrefix", "WC"))
				.andExpect(status().isConflict()).andExpect(content().string("The prefix WC is already used by another type"));
		mvc.perform(jsonBody(withToken(post("/api/equipment-types"), token), "name", "Bad " + suffix, "category", "x", "labelPrefix", "W-C"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void poolsAndStaffBlockedFromInsights() throws Exception {
		mvc.perform(withToken(get("/api/pools"), tokenFor(Role.OT_STAFF)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].code").value("MAIN"));
		mvc.perform(withToken(get("/api/insights"), tokenFor(Role.OT_STAFF))).andExpect(status().isForbidden());
	}

	private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder jsonRegister(String token, String type, String pool,
			String location) {
		String where = location == null ? "" : ",\"location\":\"" + location + "\"";
		return withToken(post("/api/equipment"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"" + type + "\",\"pool\":\"" + pool + "\"" + where + ",\"details\":{\"name\":\"New item\"}}");
	}

	private String prefixOf(TypeRef type) {
		return jdbc.queryForObject("select label_prefix from equipment_type where id = ?", String.class, type.id());
	}

}
