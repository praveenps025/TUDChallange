package nhs.ot.equiptrack.discharge;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OverrideRepository extends JpaRepository<DischargeOverride, Long> {

	Optional<DischargeOverride> findFirstByPatientIdOrderByRequestedAtDescIdDesc(long patientId);

	boolean existsByPatientIdAndStatus(long patientId, DischargeOverride.Status status);

}
