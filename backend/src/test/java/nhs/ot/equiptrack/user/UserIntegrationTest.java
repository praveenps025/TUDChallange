package nhs.ot.equiptrack.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import nhs.ot.equiptrack.ApiTestSupport;

class UserIntegrationTest extends ApiTestSupport {

	@Test
	void staffCannotManageUsers() throws Exception {
		String staff = tokenFor(Role.OT_STAFF);
		mvc.perform(withToken(get("/api/users"), staff))
				.andExpect(status().isForbidden())
				.andExpect(content().string("You don't have permission to do that"));
		mvc.perform(jsonBody(withToken(post("/api/users"), staff),
				"username", "sneaky", "name", "S", "team", "T", "role", "MANAGER", "password", "password-1"))
				.andExpect(status().isForbidden());
	}

	@Test
	void managerListsUsers() throws Exception {
		AppUser someone = createUser(Role.OT_STAFF);
		mvc.perform(withToken(get("/api/users"), tokenFor(Role.MANAGER)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.username == '" + someone.getUsername() + "')].role").value("OT_STAFF"))
				.andExpect(jsonPath("$[0].passwordHash").doesNotExist());
	}

	@Test
	void managerCreatesUserWhoCanSignIn() throws Exception {
		String username = "new-" + UUID.randomUUID().toString().substring(0, 8);
		mvc.perform(jsonBody(withToken(post("/api/users"), tokenFor(Role.MANAGER)),
				"username", username, "name", "New Starter", "team", "OT equipment team", "role", "OT_STAFF",
				"password", "first-pass-1"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.username").value(username))
				.andExpect(jsonPath("$.active").value(true));

		login(username, "first-pass-1");
	}

	@Test
	void duplicateUsernameIs409IgnoringCase() throws Exception {
		AppUser existing = createUser(Role.OT_STAFF);
		mvc.perform(jsonBody(withToken(post("/api/users"), tokenFor(Role.MANAGER)),
				"username", existing.getUsername().toUpperCase(), "name", "Copy", "team", "T", "role", "OT_STAFF",
				"password", "password-1"))
				.andExpect(status().isConflict())
				.andExpect(content().string("The username " + existing.getUsername().toUpperCase() + " is already taken"));
	}

	@Test
	void invalidUserIs400() throws Exception {
		String manager = tokenFor(Role.MANAGER);
		mvc.perform(jsonBody(withToken(post("/api/users"), manager),
				"username", "ok-name", "name", "N", "team", "T", "role", "OT_STAFF", "password", "short"))
				.andExpect(status().isBadRequest())
				.andExpect(content().string("Passwords must be 8 to 72 characters"));
		mvc.perform(jsonBody(withToken(post("/api/users"), manager),
				"username", "ok-name", "name", "N", "team", "T", "role", "ADMIN", "password", "password-1"))
				.andExpect(status().isBadRequest())
				.andExpect(content().string("The request is not valid"));
	}

	@Test
	void deactivatedUserCannotSignInAndExistingTokenStops() throws Exception {
		AppUser user = createUser(Role.OT_STAFF);
		String userToken = login(user.getUsername(), PASSWORD);

		mvc.perform(jsonBody(withToken(put("/api/users/" + user.getId()), tokenFor(Role.MANAGER)),
				"name", user.getDisplayName(), "team", user.getTeam(), "role", "OT_STAFF", "active", false))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));

		mvc.perform(withToken(get("/api/auth/me"), userToken))
				.andExpect(status().isUnauthorized())
				.andExpect(content().string("This account has been deactivated. Ask a manager to reactivate it."));
		mvc.perform(jsonBody(post("/api/auth/login"), "username", user.getUsername(), "password", PASSWORD))
				.andExpect(status().isUnauthorized())
				.andExpect(content().string("This account has been deactivated. Ask a manager to reactivate it."));
	}

	@Test
	void roleChangeAppliesToExistingToken() throws Exception {
		AppUser user = createUser(Role.OT_STAFF);
		String userToken = login(user.getUsername(), PASSWORD);
		mvc.perform(withToken(get("/api/users"), userToken)).andExpect(status().isForbidden());

		mvc.perform(jsonBody(withToken(put("/api/users/" + user.getId()), tokenFor(Role.MANAGER)),
				"name", user.getDisplayName(), "team", user.getTeam(), "role", "MANAGER", "active", true))
				.andExpect(status().isOk());

		mvc.perform(withToken(get("/api/users"), userToken)).andExpect(status().isOk());
	}

	@Test
	void managerCannotDeactivateThemselves() throws Exception {
		AppUser manager = createUser(Role.MANAGER);
		String token = login(manager.getUsername(), PASSWORD);
		mvc.perform(jsonBody(withToken(put("/api/users/" + manager.getId()), token),
				"name", "Me", "team", "T", "role", "MANAGER", "active", false))
				.andExpect(status().isConflict())
				.andExpect(content().string("You can't deactivate your own account or remove your own manager role"));
	}

	@Test
	void updateUnknownUserIs404() throws Exception {
		mvc.perform(jsonBody(withToken(put("/api/users/999999"), tokenFor(Role.MANAGER)),
				"name", "N", "team", "T", "role", "OT_STAFF", "active", true))
				.andExpect(status().isNotFound())
				.andExpect(content().string("User not found"));
	}

	@Test
	void resetPasswordAlsoUnlocks() throws Exception {
		AppUser user = createUser(Role.OT_STAFF);
		for (int i = 0; i < 5; i++) {
			mvc.perform(jsonBody(post("/api/auth/login"), "username", user.getUsername(), "password", "wrong"));
		}
		mvc.perform(jsonBody(withToken(post("/api/users/" + user.getId() + "/reset-password"), tokenFor(Role.MANAGER)),
				"password", "reset-pass-1"))
				.andExpect(status().isNoContent());

		login(user.getUsername(), "reset-pass-1");
	}

}
