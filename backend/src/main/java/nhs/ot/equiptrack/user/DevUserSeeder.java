package nhs.ot.equiptrack.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Development logins: two per role, so concurrent use can be tried in two
// browsers. Passwords come from the environment (.env), never from source.
// Existing accounts are left alone.
@Component
@Profile("dev")
class DevUserSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);
	private static final String TEAM = "OT equipment team";

	private final UserRepository users;
	private final PasswordEncoder encoder;
	private final String managerPassword;
	private final String staffPassword;

	DevUserSeeder(UserRepository users, PasswordEncoder encoder,
			@Value("${DEV_MANAGER_PASSWORD:}") String managerPassword,
			@Value("${DEV_STAFF_PASSWORD:}") String staffPassword) {
		this.users = users;
		this.encoder = encoder;
		this.managerPassword = managerPassword;
		this.staffPassword = staffPassword;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		seed("alex.morgan", "Alex Morgan", Role.MANAGER, managerPassword, "DEV_MANAGER_PASSWORD");
		seed("jordan.lee", "Jordan Lee", Role.MANAGER, managerPassword, "DEV_MANAGER_PASSWORD");
		seed("sam.patel", "Sam Patel", Role.OT_STAFF, staffPassword, "DEV_STAFF_PASSWORD");
		seed("riley.chen", "Riley Chen", Role.OT_STAFF, staffPassword, "DEV_STAFF_PASSWORD");
	}

	private void seed(String username, String name, Role role, String password, String variable) {
		if (users.findByUsername(username).isPresent()) {
			return;
		}
		if (password.isBlank()) {
			log.warn("{} is not set, so the dev user {} was not created", variable, username);
			return;
		}
		users.save(new AppUser(username, encoder.encode(password), name, TEAM, role));
		log.info("Created dev user {} ({})", username, role);
	}

}
