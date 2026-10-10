package nhs.ot.equiptrack.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// First start of a new installation (e.g. on Azure): with no users at all and
// INITIAL_MANAGER_USERNAME / INITIAL_MANAGER_PASSWORD set, create that manager
// so someone can sign in and add everyone else. It never runs once any user
// exists; remove the password setting after the first sign-in.
@Component
@Order(0)
class InitialManagerSetup implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(InitialManagerSetup.class);

	private final UserRepository users;
	private final PasswordEncoder encoder;
	private final String username;
	private final String password;
	private final String name;

	InitialManagerSetup(UserRepository users, PasswordEncoder encoder,
			@Value("${INITIAL_MANAGER_USERNAME:}") String username,
			@Value("${INITIAL_MANAGER_PASSWORD:}") String password,
			@Value("${INITIAL_MANAGER_NAME:OT Manager}") String name) {
		this.users = users;
		this.encoder = encoder;
		this.username = username;
		this.password = password;
		this.name = name;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (users.count() > 0 || username.isBlank()) {
			return;
		}
		if (password.length() < 8 || password.length() > 72) {
			log.warn("INITIAL_MANAGER_PASSWORD must be 8 to 72 characters; no manager was created");
			return;
		}
		users.save(new AppUser(username.trim(), encoder.encode(password), name.trim(), "OT equipment team", Role.MANAGER));
		log.info("Created the first manager account '{}'. Remove INITIAL_MANAGER_PASSWORD from the settings now.", username.trim());
	}

}
