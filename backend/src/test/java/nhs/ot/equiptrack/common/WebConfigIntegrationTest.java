package nhs.ot.equiptrack.common;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import nhs.ot.equiptrack.ApiTestSupport;
import nhs.ot.equiptrack.user.Role;

// The backend serves the React app at / and the API under /api
class WebConfigIntegrationTest extends ApiTestSupport {

	// Spring Boot's welcome page forwards / to index.html (MockMvc doesn't follow forwards)
	@Test
	void servesTheAppWithoutSigningIn() throws Exception {
		mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
		mvc.perform(get("/index.html")).andExpect(status().isOk()).andExpect(content().string(containsString("<div id=\"root\">")));
	}

	@Test
	void clientSideRoutesGetTheApp() throws Exception {
		mvc.perform(get("/manage/inventory")).andExpect(status().isOk()).andExpect(content().string(containsString("<div id=\"root\">")));
		mvc.perform(get("/item/CU-024")).andExpect(status().isOk()).andExpect(content().string(containsString("<div id=\"root\">")));
	}

	@Test
	void missingFilesAndApiPathsAreNotTheApp() throws Exception {
		mvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound());
		mvc.perform(get("/api/nothing-here")).andExpect(status().isUnauthorized());
		mvc.perform(withToken(get("/api/nothing-here"), tokenFor(Role.OT_STAFF)))
				.andExpect(status().isNotFound())
				.andExpect(content().string("Not found"));
	}

}
