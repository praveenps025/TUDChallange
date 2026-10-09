package nhs.ot.equiptrack.equipment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<EquipmentCategory, Long> {

	List<EquipmentCategory> findAllByOrderBySortOrderAscNameAsc();

	Optional<EquipmentCategory> findByNameIgnoreCase(String name);

}
