package nhs.ot.equiptrack.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

import nhs.ot.equiptrack.ApiTestSupport;
import nhs.ot.equiptrack.user.AppUser;
import nhs.ot.equiptrack.user.Role;

class EquipmentSearchIntegrationTest extends ApiTestSupport {

	@Test
	void filtersByTypeCategoryAndStatusInLabelOrder() throws Exception {
		TypeRef type = newType();
		String a = newEquipment(type, "available", "OT store");
		String b = newEquipment(type, "issued", "Ward 2B");
		String c = newEquipment(type, "available", "OT store");
		String token = tokenFor(Role.OT_STAFF);

		assertThat(ids(token, "type=" + type.name())).containsExactlyElementsOf(sorted(a, b, c));
		assertThat(ids(token, "category=" + type.category().toUpperCase())).containsExactlyElementsOf(sorted(a, b, c));
		assertThat(ids(token, "type=" + type.name() + "&status=available")).containsExactlyElementsOf(sorted(a, c));
		assertThat(ids(token, "type=" + type.name() + "&pool=main&status=issued")).containsExactly(b);
	}

	@Test
	void searchFindsItemsByAssetNumberSerialOrModel() throws Exception {
		TypeRef type = newType();
		String label = newEquipment(type, "available", "OT store");
		String serial = "SN-" + unique();
		jdbc.update("update equipment set asset_number = ?, serial_number = ?, model = ? where label_code = ?",
				"AST-" + serial, serial, "Model " + serial, label);
		String token = tokenFor(Role.OT_STAFF);

		assertThat(ids(token, "search=" + serial.toLowerCase())).containsExactly(label);
		assertThat(ids(token, "search=AST-" + serial)).containsExactly(label);
		assertThat(ids(token, "search=model " + serial)).containsExactly(label);
		assertThat(ids(token, "search=" + label.toLowerCase())).containsExactly(label);
		assertThat(ids(token, "search=%25" + unique())).isEmpty(); // a literal % matches nothing extra
	}

	@Test
	void pages() throws Exception {
		TypeRef type = newType();
		for (int i = 0; i < 3; i++) {
			newEquipment(type, "available", "OT store");
		}
		String token = tokenFor(Role.OT_STAFF);
		mvc.perform(withToken(get("/equipment?type=" + type.name() + "&page=1&size=2"), token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.page").value(1))
				.andExpect(jsonPath("$.size").value(2))
				.andExpect(jsonPath("$.total").value(3));
		mvc.perform(withToken(get("/equipment?size=500"), token))
				.andExpect(status().isBadRequest());
		mvc.perform(withToken(get("/equipment?status=lost"), token))
				.andExpect(status().isBadRequest())
				.andExpect(content().string("Unknown status lost. Use available, issued, inspect or missing."));
	}

	@Test
	void recentShowsWhatThisUserLastWorkedOn() throws Exception {
		AppUser user = createUser(Role.OT_STAFF);
		String token = tokenFor(user);
		TypeRef type = newType();
		String first = newEquipment(type, "inspect", "OT store");
		String second = newEquipment(type, "inspect", "OT store");
		mvc.perform(withToken(post("/equipment/" + first + "/inspect"), token)).andExpect(status().isOk());
		mvc.perform(withToken(post("/equipment/" + second + "/inspect"), token)).andExpect(status().isOk());

		String body = mvc.perform(withToken(get("/equipment/recent"), token)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat((List<String>) JsonPath.read(body, "$[*].id")).containsExactly(second, first);

		String someoneElse = mvc.perform(withToken(get("/equipment/recent"), tokenFor(Role.OT_STAFF)))
				.andReturn().getResponse().getContentAsString();
		assertThat((List<String>) JsonPath.read(someoneElse, "$[*].id")).doesNotContain(first, second);
	}

	@Test
	void referenceListsComeFromTheDatabase() throws Exception {
		TypeRef type = newType();
		String token = tokenFor(Role.OT_STAFF);
		mvc.perform(withToken(get("/equipment-types"), token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == 'Wheelchair')].category").value("Wheelchairs"))
				.andExpect(jsonPath("$[?(@.name == '" + type.name() + "')].defaultLoanDays").value(14));
		mvc.perform(withToken(get("/categories"), token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == 'Cushions')].icon").value("layers"))
				.andExpect(jsonPath("$[?(@.name == '" + type.category() + "')].types[0]").value(type.name()));
		String wards = mvc.perform(withToken(get("/locations?kind=ward"), token))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat((List<String>) JsonPath.read(wards, "$[*].kind")).containsOnly("ward");
		assertThat((List<String>) JsonPath.read(wards, "$[*].name")).contains("Ward 3A").doesNotContain("OT store");
	}

	// query is "name=value&name=value", values unencoded
	private List<String> ids(String token, String query) throws Exception {
		var request = get("/equipment");
		for (String pair : query.split("&")) {
			String[] parts = pair.split("=", 2);
			request.param(parts[0], java.net.URLDecoder.decode(parts[1], java.nio.charset.StandardCharsets.UTF_8));
		}
		String body = mvc.perform(withToken(request, token))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.items[*].id");
	}

	private static List<String> sorted(String... labels) {
		return java.util.Arrays.stream(labels).sorted().toList();
	}

}
