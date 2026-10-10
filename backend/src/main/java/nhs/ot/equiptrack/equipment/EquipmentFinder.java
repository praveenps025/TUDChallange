package nhs.ot.equiptrack.equipment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nhs.ot.equiptrack.common.ApiException;

// "Find equipment" for staff and managers: which items of a kind can be taken
// right now and where they are. When none can, the counts of those out
// (issued, being inspected, missing) explain why.
@Service
public class EquipmentFinder {

	public record AvailableItem(String id, String name, String type, String category, String location, String weightCategory,
			Integer maxKg) {
	}

	public record Availability(List<AvailableItem> items, Map<String, Long> unavailable) {
	}

	private static final int MAX_RESULTS = 100;

	private final EquipmentRepository equipment;
	private final WeightCategoryRepository weights;

	EquipmentFinder(EquipmentRepository equipment, WeightCategoryRepository weights) {
		this.equipment = equipment;
		this.weights = weights;
	}

	@Transactional(readOnly = true)
	public Availability find(String category, String type, String search, String weight) {
		Integer minKg = weight == null || weight.isBlank() ? null
				: weights.findByNameIgnoreCase(weight.trim()).map(WeightCategory::getMaxKg)
						.orElseThrow(() -> ApiException.badRequest("Unknown weight category " + weight));

		List<AvailableItem> items = equipment.findAll(matching(category, type, search, minKg, true)).stream()
				.sorted(Comparator.comparing((Equipment e) -> e.getLocation().getName()).thenComparing(Equipment::getLabelCode))
				.limit(MAX_RESULTS)
				.map(EquipmentFinder::toItem)
				.toList();

		Map<String, Long> unavailable = new LinkedHashMap<>();
		for (EquipmentStatus status : List.of(EquipmentStatus.ISSUED, EquipmentStatus.INSPECT, EquipmentStatus.MISSING)) {
			unavailable.put(status.value(), 0L);
		}
		equipment.findAll(matching(category, type, search, minKg, false))
				.forEach(e -> unavailable.merge(e.getStatus().value(), 1L, Long::sum));
		return new Availability(items, unavailable);
	}

	private static Specification<Equipment> matching(String category, String type, String search, Integer minKg, boolean available) {
		return (root, query, cb) -> {
			List<Predicate> where = new ArrayList<>();
			var status = root.get("status");
			where.add(available ? cb.equal(status, EquipmentStatus.AVAILABLE) : cb.notEqual(status, EquipmentStatus.AVAILABLE));
			Path<String> typeName = root.get("type").get("name");
			Path<String> categoryName = root.get("type").get("category").get("name");
			if (category != null && !category.isBlank()) {
				where.add(cb.equal(cb.lower(categoryName), category.trim().toLowerCase(Locale.ROOT)));
			}
			if (type != null && !type.isBlank()) {
				where.add(cb.equal(cb.lower(typeName), type.trim().toLowerCase(Locale.ROOT)));
			}
			if (search != null && !search.isBlank()) {
				String like = "%" + search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
				where.add(cb.or(
						cb.like(cb.lower(typeName), like, '\\'),
						cb.like(cb.lower(categoryName), like, '\\'),
						cb.like(cb.lower(root.<String>get("name")), like, '\\'),
						cb.like(cb.lower(root.<String>get("labelCode")), like, '\\')));
			}
			if (minKg != null) {
				// Rated for this band or a heavier one; unrated items are left out
				where.add(cb.greaterThanOrEqualTo(root.join("weightCategory").get("maxKg"), minKg));
			}
			return cb.and(where.toArray(Predicate[]::new));
		};
	}

	private static AvailableItem toItem(Equipment e) {
		WeightCategory weight = e.getWeightCategory();
		return new AvailableItem(e.getLabelCode(), e.getName(), e.getType().getName(), e.getType().getCategory().getName(),
				e.getLocation().getName(), weight == null ? null : weight.getName(), weight == null ? null : weight.getMaxKg());
	}

}
