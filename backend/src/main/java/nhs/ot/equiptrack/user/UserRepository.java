package nhs.ot.equiptrack.user;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository extends JpaRepository<AppUser, Long> {

	// lower() matches the unique index on lower(username)
	@Query("select u from AppUser u where lower(u.username) = lower(:username)")
	Optional<AppUser> findByUsername(String username);

	List<AppUser> findAllByOrderByDisplayNameAsc();

	// Sign-in bookkeeping runs as single UPDATE statements in their own
	// transaction: it must survive the 401 that follows a failed attempt, and
	// must not trip optimistic locking when several devices sign in at once.

	@Transactional
	@Modifying
	@Query("""
			update AppUser u
			set u.failedAttempts = case when u.failedAttempts + 1 >= :maxAttempts then 0 else u.failedAttempts + 1 end,
			    u.lockedUntil = case when u.failedAttempts + 1 >= :maxAttempts then :lockUntil else u.lockedUntil end
			where u.id = :id
			""")
	void recordFailedLogin(long id, int maxAttempts, Instant lockUntil);

	@Transactional
	@Modifying
	@Query("update AppUser u set u.failedAttempts = 0, u.lockedUntil = null, u.lastLoginAt = :now where u.id = :id")
	void recordSuccessfulLogin(long id, Instant now);

}
