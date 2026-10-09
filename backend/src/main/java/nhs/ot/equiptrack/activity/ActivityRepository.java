package nhs.ot.equiptrack.activity;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ActivityRepository extends JpaRepository<ActivityLog, Long> {

	List<ActivityLog> findByOrderByOccurredAtDescIdDesc(Limit limit);

	// The items a user worked on most recently
	@Query(value = """
			select equipment_id from activity_log
			where user_id = :userId and equipment_id is not null
			group by equipment_id order by max(occurred_at) desc limit :count
			""", nativeQuery = true)
	List<Long> findRecentEquipmentIds(long userId, int count);

}
