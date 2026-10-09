package nhs.ot.equiptrack.equipment;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nhs.ot.equiptrack.activity.ActivityService;
import nhs.ot.equiptrack.auth.CurrentUser;
import nhs.ot.equiptrack.common.ApiException;

@Service
public class EquipmentService {

	public record PageResponse<T>(List<T> items, int page, int size, long total) {
	}

	private static final int MAX_PAGE_SIZE = 100;
	private static final int RECENT_COUNT = 5;

	private final EquipmentRepository equipment;
	private final EquipmentLifecycle lifecycle;
	private final EquipmentViews views;
	private final ActivityService activity;

	EquipmentService(EquipmentRepository equipment, EquipmentLifecycle lifecycle, EquipmentViews views, ActivityService activity) {
		this.equipment = equipment;
		this.lifecycle = lifecycle;
		this.views = views;
		this.activity = activity;
	}

	@Transactional(readOnly = true)
	public EquipmentResponse get(String label) {
		return views.of(find(label));
	}

	@Transactional(readOnly = true)
	PageResponse<EquipmentResponse> search(EquipmentSearch filters, int page, int size) {
		if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
			throw ApiException.badRequest("page must be 0 or more and size between 1 and " + MAX_PAGE_SIZE);
		}
		Page<Equipment> result = equipment.findAll(filters.toSpecification(), PageRequest.of(page, size, Sort.by("labelCode")));
		return new PageResponse<>(views.of(result.getContent()), page, size, result.getTotalElements());
	}

	// What this user scanned or changed most recently, newest first
	@Transactional(readOnly = true)
	public List<EquipmentResponse> recent(CurrentUser user) {
		List<Long> ids = activity.recentEquipmentIds(user, RECENT_COUNT);
		Map<Long, Integer> order = ids.stream().collect(Collectors.toMap(Function.identity(), ids::indexOf));
		List<Equipment> items = equipment.findByIdIn(ids).stream()
				.sorted(Comparator.comparing(e -> order.get(e.getId())))
				.toList();
		return views.of(items);
	}

	@Transactional
	public EquipmentResponse returnItem(CurrentUser user, String label, boolean clean) {
		Equipment item = find(label);
		lifecycle.returnItem(user, item, clean);
		return views.of(item);
	}

	@Transactional
	public EquipmentResponse inspect(CurrentUser user, String label) {
		Equipment item = find(label);
		lifecycle.inspect(user, item);
		return views.of(item);
	}

	public Equipment find(String label) {
		return equipment.findByLabel(label.trim()).orElseThrow(() -> ApiException.notFound("No equipment with ID " + label));
	}

}
