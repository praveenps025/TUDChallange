package nhs.ot.equiptrack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

// Flyway runs the migrations against a throwaway Postgres; check the
// reference data and the constraints the services will rely on.
@IntegrationTest
class SchemaMigrationTest {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void loadsReferenceData() {
		assertThat(jdbc.queryForList("select name from equipment_type order by sort_order", String.class))
				.containsExactly("Wheelchair", "Pressure cushion", "Sliding board");
		assertThat(jdbc.queryForObject("select count(*) from equipment_pool", Integer.class)).isEqualTo(4);
		assertThat(jdbc.queryForObject("select count(*) from location where name = 'OT store'", Integer.class)).isEqualTo(1);
	}

	@Test
	void rejectsUnknownStatus() {
		assertThatThrownBy(() -> insertEquipment("XX-001", "lost"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void labelCodesAreUniqueAndUppercase() {
		insertEquipment("XX-002", "available");
		assertThatThrownBy(() -> insertEquipment("XX-002", "available"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertEquipment("xx-003", "available"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void usernamesAreUniqueIgnoringCase() {
		insertUser("sam");
		assertThatThrownBy(() -> insertUser("SAM")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void updatedAtIsMaintained() {
		insertUser("riley");
		jdbc.update("update app_user set updated_at = now() - interval '1 day' where username = 'riley'");
		jdbc.update("update app_user set team = 'Other team' where username = 'riley'");
		assertThat(jdbc.queryForObject(
				"select updated_at > now() - interval '1 minute' from app_user where username = 'riley'", Boolean.class))
				.isTrue();
	}

	private void insertEquipment(String code, String status) {
		jdbc.update("""
				insert into equipment (label_code, name, type_id, pool_id, status, location_id)
				values (?, 'Test item',
				        (select id from equipment_type where name = 'Wheelchair'),
				        (select id from equipment_pool where code = 'MAIN'),
				        ?, (select id from location where name = 'OT store'))
				""", code, status);
	}

	private void insertUser(String username) {
		jdbc.update("""
				insert into app_user (username, password_hash, display_name, team, role)
				values (?, 'x', 'Test user', 'OT equipment team', 'OT_STAFF')
				""", username);
	}

}
