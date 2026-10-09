package nhs.ot.equiptrack.equipment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface TypeRepository extends JpaRepository<EquipmentType, Long> {

	@EntityGraph(attributePaths = "category")
	List<EquipmentType> findAllByOrderBySortOrderAscNameAsc();

	Optional<EquipmentType> findByName(String name);

	Optional<EquipmentType> findByLabelPrefix(String labelPrefix);

	// Locks the type while a new item takes the next number, so two
	// managers registering at once can't get the same ID
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select t from EquipmentType t where t.name = :name")
	Optional<EquipmentType> findByNameForUpdate(String name);

}
