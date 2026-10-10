package nhs.ot.equiptrack.equipment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LoanRepository extends JpaRepository<Loan, Long> {

	@Query("select l from Loan l where l.equipment.id = :equipmentId and l.returnedAt is null")
	Optional<Loan> findOpen(long equipmentId);

	@Query("select l from Loan l where l.equipment.id in :equipmentIds and l.returnedAt is null")
	List<Loan> findOpenFor(Collection<Long> equipmentIds);

	@EntityGraph(attributePaths = { "equipment.type.category", "equipment.location", "ward" })
	@Query("select l from Loan l where l.patient.id = :patientId and l.returnedAt is null order by l.issuedAt")
	List<Loan> findOpenForPatient(long patientId);

	@EntityGraph(attributePaths = { "equipment", "ward" })
	@Query("select l from Loan l where l.returnedAt is null and l.dueAt <= :by order by l.dueAt")
	List<Loan> findOpenDueBy(Instant by);

	// Activity in a period [from, to), for Insights

	@Query("select count(l) from Loan l where l.issuedAt >= :from and l.issuedAt < :to")
	long countIssuedBetween(Instant from, Instant to);

	@Query("select count(l) from Loan l where l.returnedAt >= :from and l.returnedAt < :to")
	long countReturnedBetween(Instant from, Instant to);

	@Query("select count(l) from Loan l where l.returnedAt >= :from and l.returnedAt < :to and l.returnedAt <= l.dueAt")
	long countReturnedOnTimeBetween(Instant from, Instant to);

}
