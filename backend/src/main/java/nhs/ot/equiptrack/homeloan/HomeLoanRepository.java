package nhs.ot.equiptrack.homeloan;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface HomeLoanRepository extends JpaRepository<HomeLoanRequest, Long> {

	@EntityGraph(attributePaths = { "requestedBy", "decidedBy" })
	Optional<HomeLoanRequest> findFirstByPatientIdOrderByRequestedAtDescIdDesc(long patientId);

	boolean existsByPatientIdAndStatus(long patientId, HomeLoanRequest.Status status);

	// Waiting for a manager, oldest first
	@EntityGraph(attributePaths = { "patient.ward", "requestedBy" })
	List<HomeLoanRequest> findByStatusOrderByRequestedAtAsc(HomeLoanRequest.Status status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from HomeLoanRequest r where r.id = :id")
	Optional<HomeLoanRequest> findByIdForUpdate(long id);

}
