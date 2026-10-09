package nhs.ot.equiptrack.waitlist;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WaitlistRepository extends JpaRepository<WaitlistRequest, Long> {

	// Oldest first: the order requests are served in
	@EntityGraph(attributePaths = { "ward", "type.category" })
	@Query("select w from WaitlistRequest w where w.status = nhs.ot.equiptrack.waitlist.WaitlistRequest.Status.OPEN order by w.requestedAt, w.id")
	List<WaitlistRequest> findOpenOldestFirst();

}
