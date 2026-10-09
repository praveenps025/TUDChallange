package nhs.ot.equiptrack.audit;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AuditWalkRepository extends JpaRepository<AuditWalk, Long> {

	// The walk in progress: the most recently started open one
	@EntityGraph(attributePaths = "startedBy")
	Optional<AuditWalk> findFirstByStatusOrderByStartedAtDescIdDesc(AuditWalk.Status status);

	@Query(value = "select count(*) from audit_walk_item where audit_walk_id = :walkId", nativeQuery = true)
	long countExpected(long walkId);

	@Query(value = """
			select count(*) from audit_walk_item i
			where i.audit_walk_id = :walkId
			  and exists (select 1 from audit_scan s where s.audit_walk_id = i.audit_walk_id and s.equipment_id = i.equipment_id)
			""", nativeQuery = true)
	long countFound(long walkId);

	@Query(value = """
			select i.equipment_id from audit_walk_item i
			where i.audit_walk_id = :walkId
			  and not exists (select 1 from audit_scan s where s.audit_walk_id = i.audit_walk_id and s.equipment_id = i.equipment_id)
			""", nativeQuery = true)
	List<Long> findNotYetFound(long walkId);

}
