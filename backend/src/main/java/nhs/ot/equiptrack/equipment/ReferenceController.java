package nhs.ot.equiptrack.equipment;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Reference lists, so screens build their filters and pickers from data
@RestController
class ReferenceController {

	record TypeResponse(String name, String category, int defaultLoanDays) {
	}

	record CategoryResponse(String name, String icon, List<String> types) {
	}

	record LocationResponse(String name, String kind) {
	}

	private final TypeRepository types;
	private final CategoryRepository categories;
	private final LocationRepository locations;

	ReferenceController(TypeRepository types, CategoryRepository categories, LocationRepository locations) {
		this.types = types;
		this.categories = categories;
		this.locations = locations;
	}

	@GetMapping("/equipment-types")
	@Transactional(readOnly = true)
	List<TypeResponse> types() {
		return types.findAllByOrderBySortOrderAscNameAsc().stream()
				.map(t -> new TypeResponse(t.getName(), t.getCategory().getName(), t.getDefaultLoanDays()))
				.toList();
	}

	@GetMapping("/categories")
	@Transactional(readOnly = true)
	List<CategoryResponse> categories() {
		List<EquipmentType> allTypes = types.findAllByOrderBySortOrderAscNameAsc();
		return categories.findAllByOrderBySortOrderAscNameAsc().stream()
				.map(c -> new CategoryResponse(c.getName(), c.getIconKey(), allTypes.stream()
						.filter(t -> t.getCategory().getId().equals(c.getId())).map(EquipmentType::getName).toList()))
				.toList();
	}

	// kind: ward, store, workshop or other
	@GetMapping("/locations")
	List<LocationResponse> locations(@RequestParam(required = false) String kind) {
		return locations.findByActiveTrueOrderByKindAscNameAsc().stream()
				.filter(l -> kind == null || l.getKind().equalsIgnoreCase(kind))
				.map(l -> new LocationResponse(l.getName(), l.getKind()))
				.toList();
	}

}
