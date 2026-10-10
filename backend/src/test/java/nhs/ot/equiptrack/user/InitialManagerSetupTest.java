package nhs.ot.equiptrack.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class InitialManagerSetupTest {

	private final UserRepository users = mock(UserRepository.class);
	private final PasswordEncoder encoder = mock(PasswordEncoder.class);

	@Test
	void createsTheFirstManagerOnAnEmptyInstallation() {
		when(users.count()).thenReturn(0L);
		when(encoder.encode("first-pass-1")).thenReturn("hashed");

		new InitialManagerSetup(users, encoder, "pat.manager", "first-pass-1", "Pat Manager").run(null);

		ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
		verify(users).save(saved.capture());
		assertThat(saved.getValue().getUsername()).isEqualTo("pat.manager");
		assertThat(saved.getValue().getRole()).isEqualTo(Role.MANAGER);
		assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
	}

	@Test
	void doesNothingOnceAnyUserExists() {
		when(users.count()).thenReturn(3L);
		new InitialManagerSetup(users, encoder, "pat.manager", "first-pass-1", "Pat Manager").run(null);
		verify(users, never()).save(any());
	}

	@Test
	void doesNothingWithoutSettingsOrWithAWeakPassword() {
		when(users.count()).thenReturn(0L);
		new InitialManagerSetup(users, encoder, "", "", "Pat Manager").run(null);
		new InitialManagerSetup(users, encoder, "pat.manager", "short", "Pat Manager").run(null);
		verify(users, never()).save(any());
	}

}
