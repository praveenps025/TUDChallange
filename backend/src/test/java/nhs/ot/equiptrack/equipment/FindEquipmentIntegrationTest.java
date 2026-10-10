package nhs.ot.equiptrack.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;

import nhs.ot.equiptrack.ApiTestSupport;
import nhs.ot.equiptrack.user.Role;

class FindEquipmentIntegrationTest extends ApiTestSupport {

	@Test
	void listsOnlyAvailableItemsWithLocationAndWeight() throws Exception {
		TypeRef type = newType();
		String inWorkshop = newEquipment(type, "available", "Workshop");
		String inStore = newEquipment(type, "available", "OT store");
		newEquipment(type, "issued", "Ward 2B");
		newEquipment(type, "missing", "Ward 3B");
		rate(inStore, "Bariatric");

		Map<String, Object> result = find(tokenFor(Role.OT_STAFF), "category", type.category());
		List<Map<String, Object>> items = items(result);
		assertThat(items).extracting(i -> i.get("id")).containsExactly(inStore, inWorkshop); // grouped by location
		assertThat(items.getFirst()).containsEntry("type", type.name()).containsEntry("location", "OT store")
				.containsEntry("weightCategory", "Bariatric").containsEntry("maxKg", 250);
		assertThat(items.get(1)).containsEntry("location", "Workshop").containsEntry("weightCategory", null);
		assertThat(result.get("unavailable")).isEqualTo(Map.of("issued", 1, "inspect", 0, "missing", 1));
	}

	@Test
	void weightFilterIncludesHeavierBands() throws Exception {
		TypeRef type = newType();
		String standard = newEquipment(type, "available", "OT store");
		String overweight = newEquipment(type, "available", "OT store");
		String bariatric = newEquipment(type, "available", "OT store");
		newEquipment(type, "available", "OT store"); // not rated
		rate(standard, "Standard");
		rate(overweight, "Overweight");
		rate(bariatric, "Bariatric");
		String token = tokenFor(Role.OT_STAFF);

		assertThat(items(find(token, "type", type.name(), "weight", "overweight"))).extracting(i -> i.get("id"))
				.containsExactlyInAnyOrder(overweight, bariatric);
		assertThat(items(find(token, "type", type.name(), "weight", "Underweight"))).hasSize(3);
		assertThat(items(find(token, "type", type.name()))).hasSize(4);
	}

	@Test
	void nothingAvailableExplainsWhy() throws Exception {
		TypeRef type = newType();
		newEquipment(type, "issued", "Ward 2B");
		newEquipment(type, "inspect", "OT store");

		Map<String, Object> result = find(tokenFor(Role.OT_STAFF), "search", type.name().toLowerCase());
		assertThat(items(result)).isEmpty();
		assertThat(result.get("unavailable")).isEqualTo(Map.of("issued", 1, "inspect", 1, "missing", 0));
	}

	@Test
	void unknownWeightCategoryIs400() throws Exception {
		mvc.perform(withToken(get("/equipment/available").param("weight", "Huge"), tokenFor(Role.OT_STAFF)))
				.andExpect(status().isBadRequest())
				.andExpect(content().string("Unknown weight category Huge"));
	}

	@Test
	void weightCategoriesAndRegistration() throws Exception {
		mvc.perform(withToken(get("/weight-categories"), tokenFor(Role.OT_STAFF)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("Underweight"))
				.andExpect(jsonPath("$[3].name").value("Bariatric"))
				.andExpect(jsonPath("$[3].maxKg").value(250));

		String token = tokenFor(Role.MANAGER);
		TypeRef type = newType();
		mvc.perform(withToken(post("/equipment"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"" + type.name() + "\",\"pool\":\"MAIN\",\"details\":{\"name\":\"Wide chair\",\"weightCategory\":\"overweight\"}}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.weightCategory").value("Overweight"))
				.andExpect(jsonPath("$.weightMaxKg").value(170));
		mvc.perform(withToken(post("/equipment"), token).contentType(MediaType.APPLICATION_JSON)
				.content("{\"type\":\"" + type.name() + "\",\"pool\":\"MAIN\",\"details\":{\"name\":\"x\",\"weightCategory\":\"Giant\"}}"))
				.andExpect(status().isBadRequest())
				.andExpect(content().string("Unknown weight category Giant"));
	}

	private void rate(String label, String band) {
		jdbc.update("update equipment set weight_category_id = (select id from weight_category where name = ?) where label_code = ?", band, label);
	}

	private Map<String, Object> find(String token, String... params) throws Exception {
		var request = get("/equipment/available");
		for (int i = 0; i < params.length; i += 2) {
			request.param(params[i], params[i + 1]);
		}
		String body = mvc.perform(withToken(request, token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$");
	}

	@SuppressWarnings("unchecked")
	private static List<Map<String, Object>> items(Map<String, Object> result) {
		return (List<Map<String, Object>>) result.get("items");
	}

}
