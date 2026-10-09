package nhs.ot.equiptrack.equipment;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TypeRepository extends JpaRepository<EquipmentType, Long> {

	@EntityGraph(attributePaths = "category")
	List<EquipmentType> findAllByOrderBySortOrderAscNameAsc();

}
