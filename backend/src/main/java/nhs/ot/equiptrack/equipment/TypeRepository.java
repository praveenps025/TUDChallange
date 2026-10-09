package nhs.ot.equiptrack.equipment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TypeRepository extends JpaRepository<EquipmentType, Long> {

	@EntityGraph(attributePaths = "category")
	List<EquipmentType> findAllByOrderBySortOrderAscNameAsc();

	Optional<EquipmentType> findByName(String name);

}
