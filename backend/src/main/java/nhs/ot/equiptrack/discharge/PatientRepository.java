package nhs.ot.equiptrack.discharge;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PatientRepository extends JpaRepository<Patient, Long> {

	@EntityGraph(attributePaths = "ward")
	Optional<Patient> findByMrnHash(String mrnHash);

	@EntityGraph(attributePaths = "ward")
	List<Patient> findByMrnSuffix(String mrnSuffix);

	// Patients due to leave who still have equipment out
	@EntityGraph(attributePaths = "ward")
	@Query("""
			select p from Patient p
			where p.dischargePlannedOn <= :by
			  and exists (select 1 from Loan l where l.patient = p and l.returnedAt is null)
			order by p.dischargePlannedOn
			""")
	List<Patient> findDueForDischargeWithOpenLoans(LocalDate by);

}
