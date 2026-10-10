package nhs.ot.equiptrack.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import nhs.ot.equiptrack.discharge.MrnHasher;

// Sample logins for development and demo servers: two per role, so concurrent
// use can be tried in two browsers. Passwords come from the environment (.env
// or App Service settings), never from source. Existing accounts are left alone.
@Component
@Profile({ "dev", "demo" })
class DevUserSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);
	private static final String TEAM = "OT equipment team";

	private final UserRepository users;
	private final PasswordEncoder encoder;
	private final JdbcTemplate jdbc;
	private final MrnHasher mrnHasher;
	private final String managerPassword;
	private final String staffPassword;

	DevUserSeeder(UserRepository users, PasswordEncoder encoder, JdbcTemplate jdbc, MrnHasher mrnHasher,
			@Value("${DEV_MANAGER_PASSWORD:}") String managerPassword,
			@Value("${DEV_STAFF_PASSWORD:}") String staffPassword) {
		this.users = users;
		this.encoder = encoder;
		this.jdbc = jdbc;
		this.mrnHasher = mrnHasher;
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
		attributeSampleData();
		// The sample patient's fictional MRN, hashed with this server's key
		jdbc.update("update patient set mrn_hash = ? where mrn_hash = rpad('sample-patient-4821', 64, '-')", mrnHasher.hash("9990004821"));
	}

	// The Flyway sample data runs before any user exists; credit it to Alex,
	// as the mock does ("Started 09:10 · Alex Morgan")
	private void attributeSampleData() {
		users.findByUsername("alex.morgan").ifPresent(alex -> {
			jdbc.update("update audit_walk set started_by = ? where started_by is null", alex.getId());
			jdbc.update("update activity_log set user_id = ? where user_id is null", alex.getId());
			jdbc.update("update waitlist_request set requested_by = ? where requested_by is null", alex.getId());
			jdbc.update("update loan set issued_by = ? where issued_by is null", alex.getId());
		});
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
