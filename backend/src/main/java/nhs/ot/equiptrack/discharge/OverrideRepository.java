package nhs.ot.equiptrack.discharge;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverrideRepository extends JpaRepository<DischargeOverride, Long> {

	@EntityGraph(attributePaths = { "requestedBy", "decidedBy" })
	Optional<DischargeOverride> findFirstByPatientIdOrderByRequestedAtDescIdDesc(long patientId);

	boolean existsByPatientIdAndStatus(long patientId, DischargeOverride.Status status);

	// Waiting for a manager, oldest first
	@EntityGraph(attributePaths = { "patient.ward", "requestedBy" })
	List<DischargeOverride> findByStatusOrderByRequestedAtAsc(DischargeOverride.Status status);

}
