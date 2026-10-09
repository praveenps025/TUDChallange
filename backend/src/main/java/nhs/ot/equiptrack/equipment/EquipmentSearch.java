package nhs.ot.equiptrack.equipment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

import nhs.ot.equiptrack.common.ApiException;

// Filters for GET /equipment. Every filter is optional; names match the
// reference data (type and category names, pool codes), never Java constants.
record EquipmentSearch(String status, String type, String category, String pool, String search) {

	Specification<Equipment> toSpecification() {
		EquipmentStatus wanted = parseStatus();
		return (root, query, cb) -> {
			List<Predicate> where = new ArrayList<>();
			if (wanted != null) {
				where.add(cb.equal(root.get("status"), wanted));
			}
			if (hasText(type)) {
				where.add(cb.equal(cb.lower(root.get("type").get("name")), type.trim().toLowerCase(Locale.ROOT)));
			}
			if (hasText(category)) {
				where.add(cb.equal(cb.lower(root.get("type").get("category").get("name")), category.trim().toLowerCase(Locale.ROOT)));
			}
			if (hasText(pool)) {
				where.add(cb.equal(cb.lower(root.get("pool").get("code")), pool.trim().toLowerCase(Locale.ROOT)));
			}
			if (hasText(search)) {
				// For finding an item whose label is lost: ID, name, asset number, serial number or model
				String like = "%" + search.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
				where.add(cb.or(
						cb.like(cb.lower(root.get("labelCode")), like, '\\'),
						cb.like(cb.lower(root.get("name")), like, '\\'),
						cb.like(cb.lower(cb.coalesce(root.get("assetNumber"), "")), like, '\\'),
						cb.like(cb.lower(cb.coalesce(root.get("serialNumber"), "")), like, '\\'),
						cb.like(cb.lower(cb.coalesce(root.get("model"), "")), like, '\\')));
			}
			return cb.and(where.toArray(Predicate[]::new));
		};
	}

	private EquipmentStatus parseStatus() {
		if (!hasText(status)) {
			return null;
		}
		try {
			return EquipmentStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw ApiException.badRequest("Unknown status " + status + ". Use available, issued, inspect or missing.");
		}
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

}
