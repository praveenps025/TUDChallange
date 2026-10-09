package nhs.ot.equiptrack.equipment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentPoolRepository extends JpaRepository<EquipmentPool, Long> {

	Optional<EquipmentPool> findByCodeIgnoreCase(String code);

	List<EquipmentPool> findAllByOrderBySortOrderAscNameAsc();

}
