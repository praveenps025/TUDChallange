package nhs.ot.equiptrack.equipment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

	// Label codes are stored uppercase; scanned or typed IDs may not be
	@EntityGraph(attributePaths = { "type.category", "location" })
	@Query("select e from Equipment e where e.labelCode = upper(:labelCode)")
	Optional<Equipment> findByLabel(String labelCode);

	@EntityGraph(attributePaths = { "type.category", "location" })
	List<Equipment> findByIdIn(Collection<Long> ids);

	@EntityGraph(attributePaths = { "type", "location" })
	Optional<Equipment> findFirstByTypeIdAndStatusOrderByLabelCode(long typeId, EquipmentStatus status);

	long countByStatus(EquipmentStatus status);

	// Available items per type, for waitlist "available now"
	@Query("select e.type.id as typeId, count(e) as total from Equipment e where e.status = :status group by e.type.id")
	List<TypeCount> countByTypeWithStatus(EquipmentStatus status);

	// Items per category and status, for Insights
	@Query("""
			select e.type.category.id as categoryId, e.status as status, count(e) as total
			from Equipment e group by e.type.category.id, e.status
			""")
	List<CategoryStatusCount> countByCategoryAndStatus();

	// Items in a store or workshop that nobody has scanned recently
	@Query("""
			select count(e) from Equipment e
			where e.status in (nhs.ot.equiptrack.equipment.EquipmentStatus.AVAILABLE, nhs.ot.equiptrack.equipment.EquipmentStatus.INSPECT)
			  and (e.lastSeenAt is null or e.lastSeenAt < :before)
			""")
	long countUnseenSince(Instant before);

	interface TypeCount {
		Long getTypeId();

		long getTotal();
	}

	interface CategoryStatusCount {
		Long getCategoryId();

		EquipmentStatus getStatus();

		long getTotal();
	}

}
