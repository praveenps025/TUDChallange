package nhs.ot.equiptrack.equipment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, Long> {

	Optional<Location> findByName(String name);

	List<Location> findByActiveTrueOrderByKindAscNameAsc();

}
