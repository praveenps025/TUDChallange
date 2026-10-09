package nhs.ot.equiptrack.user;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "app_user")
public class AppUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String username;

	private String passwordHash;

	private String displayName;

	private String team;

	@Enumerated(EnumType.STRING)
	private Role role;

	private boolean active = true;

	private int failedAttempts;

	private Instant lockedUntil;

	private Instant lastLoginAt;

	@Version
	private long version;

	protected AppUser() {
	}

	public AppUser(String username, String passwordHash, String displayName, String team, Role role) {
		this.username = username;
		this.passwordHash = passwordHash;
		this.displayName = displayName;
		this.team = team;
		this.role = role;
	}

	public boolean isLocked(Instant now) {
		return lockedUntil != null && lockedUntil.isAfter(now);
	}

	public void changePassword(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	// A manager reset also lifts any lock from failed sign-ins
	public void resetPassword(String passwordHash) {
		this.passwordHash = passwordHash;
		this.failedAttempts = 0;
		this.lockedUntil = null;
	}

	public void updateProfile(String displayName, String team, Role role, boolean active) {
		this.displayName = displayName;
		this.team = team;
		this.role = role;
		this.active = active;
	}

	public Long getId() {
		return id;
	}

	public String getUsername() {
		return username;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getTeam() {
		return team;
	}

	public Role getRole() {
		return role;
	}

	public boolean isActive() {
		return active;
	}

	public int getFailedAttempts() {
		return failedAttempts;
	}

	public Instant getLockedUntil() {
		return lockedUntil;
	}

	public Instant getLastLoginAt() {
		return lastLoginAt;
	}

}
