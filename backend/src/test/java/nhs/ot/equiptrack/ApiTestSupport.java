package nhs.ot.equiptrack;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import nhs.ot.equiptrack.user.AppUser;
import nhs.ot.equiptrack.user.Role;
import nhs.ot.equiptrack.user.UserRepository;

// Helpers for API tests. Each test creates its own users with unique names,
// so tests never depend on each other's data.
@IntegrationTest
public abstract class ApiTestSupport {

	protected static final String PASSWORD = "correct-horse-1";

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected UserRepository users;

	@Autowired
	protected PasswordEncoder encoder;

	protected AppUser createUser(Role role) {
		String username = role.name().toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8);
		return users.save(new AppUser(username, encoder.encode(PASSWORD), "Test " + username, "OT equipment team", role));
	}

	protected String login(String username, String password) throws Exception {
		String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(json("username", username, "password", password)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.token");
	}

	protected String tokenFor(Role role) throws Exception {
		return login(createUser(role).getUsername(), PASSWORD);
	}

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
