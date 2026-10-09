package nhs.ot.equiptrack.activity;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<ActivityLog, Long> {

	List<ActivityLog> findByOrderByOccurredAtDescIdDesc(Limit limit);

}
