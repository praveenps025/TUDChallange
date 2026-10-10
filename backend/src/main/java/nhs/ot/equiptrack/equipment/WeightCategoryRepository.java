package nhs.ot.equiptrack.equipment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WeightCategoryRepository extends JpaRepository<WeightCategory, Long> {

	List<WeightCategory> findAllByOrderByMaxKgAsc();

	Optional<WeightCategory> findByNameIgnoreCase(String name);

}
