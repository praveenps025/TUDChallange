package nhs.ot.equiptrack.audit;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

	@Modifying
	@Query(value = "insert into audit_walk_location (audit_walk_id, location_id) values (:walkId, :locationId)", nativeQuery = true)
	void addLocation(long walkId, long locationId);

	// Everything currently in the walk's locations is expected to be found
	@Modifying
	@Query(value = """
			insert into audit_walk_item (audit_walk_id, equipment_id, expected_location_id)
			select :walkId, e.id, e.location_id from equipment e
			where e.location_id in (select location_id from audit_walk_location where audit_walk_id = :walkId)
			""", nativeQuery = true)
	void captureExpectedItems(long walkId);

	// Returns 1 for a first scan, 0 if the item was already scanned on this walk
	@Modifying
	@Query(value = """
			insert into audit_scan (audit_walk_id, equipment_id, scanned_by) values (:walkId, :equipmentId, :userId)
			on conflict (audit_walk_id, equipment_id) do nothing
			""", nativeQuery = true)
	int recordScan(long walkId, long equipmentId, long userId);

}
