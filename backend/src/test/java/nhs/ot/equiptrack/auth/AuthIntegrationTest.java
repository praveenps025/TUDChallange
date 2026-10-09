package nhs.ot.equiptrack.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import nhs.ot.equiptrack.ApiTestSupport;
import nhs.ot.equiptrack.user.AppUser;
import nhs.ot.equiptrack.user.Role;

class AuthIntegrationTest extends ApiTestSupport {

	@Autowired
	JwtEncoder jwtEncoder;

	@Test
	void loginReturnsTokenAndUser() throws Exception {
		AppUser user = createUser(Role.OT_STAFF);
		mvc.perform(jsonBody(post("/auth/login"), "username", user.getUsername().toUpperCase(), "password", PASSWORD))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token", notNullValue()))
				.andExpect(jsonPath("$.expiresAt", notNullValue()))
				.andExpect(jsonPath("$.user.name").value(user.getDisplayName()))
				.andExpect(jsonPath("$.user.team").value("OT equipment team"))
				.andExpect(jsonPath("$.user.role").value("OT_STAFF"));

		assertThat(users.findById(user.getId()).orElseThrow().getLastLoginAt()).isNotNull();
	}

	@Test
	void meReturnsTheTokensOwner() throws Exception {
		AppUser user = createUser(Role.MANAGER);
		String token = login(user.getUsername(), PASSWORD);
		mvc.perform(withToken(get("/auth/me"), token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value(user.getDisplayName()))
				.andExpect(jsonPath("$.role").value("MANAGER"));
	}

	@Test
	void wrongPasswordIs401PlainText() throws Exception {
		AppUser user = createUser(Role.OT_STAFF);
		mvc.perform(jsonBody(post("/auth/login"), "username", user.getUsername(), "password", "wrong-password"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("Content-Type", "text/plain;charset=UTF-8"))
				.andExpect(content().string(AuthService.BAD_LOGIN));
	}

	@Test
	void unknownUserIs401WithSameMessage() throws Exception {
		mvc.perform(jsonBody(post("/auth/login"), "username", "nobody-here", "password", PASSWORD))
				.andExpect(status().isUnauthorized())
				.andExpect(content().string(AuthService.BAD_LOGIN));
	}

	@Test
	void blankCredentialsAre400() throws Exception {
		mvc.perform(jsonBody(post("/auth/login"), "username", "", "password", PASSWORD))
				.andExpect(status().isBadRequest())
				.andExpect(content().string("Enter your username"));
	}

	@Test
	void requestsWithoutTokenAre401() throws Exception {
		mvc.perform(get("/auth/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().string("Please sign in"));
	}

	@Test
	void invalidTokenIs401() throws Exception {
		mvc.perform(withToken(get("/auth/me"), "not-a-real-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().string("Your session has expired. Please sign in again."));
	}

	@Test
	void expiredTokenIs401() throws Exception {
		AppUser user = createUser(Role.OT_STAFF);
		Instant issued = Instant.now().minus(9, ChronoUnit.HOURS);
		JwtClaimsSet claims = JwtClaimsSet.builder().issuer(TokenService.ISSUER).subject(user.getUsername())
				.issuedAt(issued).expiresAt(issued.plus(8, ChronoUnit.HOURS)).build();
		String expired = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();

		mvc.perform(withToken(get("/auth/me"), expired)).andExpect(status().isUnauthorized());
	}

	@Test
	void locksAccountAfterRepeatedFailures() throws Exception {
		AppUser user = createUser(Role.OT_STAFF);
		for (int i = 1; i <= 4; i++) {
			mvc.perform(jsonBody(post("/auth/login"), "username", user.getUsername(), "password", "wrong"))
					.andExpect(content().string(AuthService.BAD_LOGIN));
		}
		mvc.perform(jsonBody(post("/auth/login"), "username", user.getUsername(), "password", "wrong"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().string("Too many failed sign-in attempts. Try again in 15 minutes."));

		// Even the right password is refused while locked
		mvc.perform(jsonBody(post("/auth/login"), "username", user.getUsername(), "password", PASSWORD))
				.andExpect(status().isUnauthorized())
				.andExpect(content().string("Too many failed sign-in attempts. Try again in 15 minutes."));
	}

	@Test
	void successfulLoginResetsFailureCount() throws Exception {
		AppUser user = createUser(Role.OT_STAFF);
		for (int i = 0; i < 4; i++) {
			mvc.perform(jsonBody(post("/auth/login"), "username", user.getUsername(), "password", "wrong"));
		}
		login(user.getUsername(), PASSWORD);
		mvc.perform(jsonBody(post("/auth/login"), "username", user.getUsername(), "password", "wrong"))
				.andExpect(content().string(AuthService.BAD_LOGIN));
		login(user.getUsername(), PASSWORD);
	}

	@Test
	void sameAccountOnSeveralDevicesStaysSignedIn() throws Exception {
		AppUser user = createUser(Role.OT_STAFF);
		String phone = login(user.getUsername(), PASSWORD);
		String desktop = login(user.getUsername(), PASSWORD);
		String otherUser = tokenFor(Role.MANAGER);

		// Signing out is client-side, and new sign-ins don't end earlier sessions
		mvc.perform(withToken(get("/auth/me"), phone)).andExpect(jsonPath("$.name").value(user.getDisplayName()));
		mvc.perform(withToken(get("/auth/me"), desktop)).andExpect(jsonPath("$.name").value(user.getDisplayName()));
		mvc.perform(withToken(get("/auth/me"), otherUser)).andExpect(jsonPath("$.role").value("MANAGER"));
	}

	@Test
	void changePassword() throws Exception {
		AppUser user = createUser(Role.OT_STAFF);
		String token = login(user.getUsername(), PASSWORD);

		mvc.perform(jsonBody(withToken(post("/auth/change-password"), token),
				"currentPassword", "wrong", "newPassword", "new-password-9"))
				.andExpect(status().isBadRequest())
				.andExpect(content().string("Your current password is incorrect"));

		mvc.perform(jsonBody(withToken(post("/auth/change-password"), token),
				"currentPassword", PASSWORD, "newPassword", "short"))
				.andExpect(status().isBadRequest())
				.andExpect(content().string("The new password must be 8 to 72 characters"));

		mvc.perform(jsonBody(withToken(post("/auth/change-password"), token),
				"currentPassword", PASSWORD, "newPassword", "new-password-9"))
				.andExpect(status().isNoContent());

		login(user.getUsername(), "new-password-9");
		mvc.perform(jsonBody(post("/auth/login"), "username", user.getUsername(), "password", PASSWORD))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void healthIsPublic() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

}
