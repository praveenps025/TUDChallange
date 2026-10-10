package nhs.ot.equiptrack.equipment;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import nhs.ot.equiptrack.auth.CurrentUser;

// Reference lists, so screens build their filters and pickers from data
@RestController
class ReferenceController {

	record TypeResponse(String name, String category, String labelPrefix, int defaultLoanDays) {

		static TypeResponse of(EquipmentType t) {
			return new TypeResponse(t.getName(), t.getCategory().getName(), t.getLabelPrefix(), t.getDefaultLoanDays());
		}
	}

	record NewTypeRequest(@NotBlank(message = "Enter a name for the type") @Size(max = 60, message = "Name is too long") String name,
			@NotBlank(message = "Choose or enter a category") @Size(max = 60, message = "Category is too long") String category,
			@NotBlank(message = "Enter a label prefix") String labelPrefix,
			Integer defaultLoanDays) {
	}

	record PoolResponse(String code, String name, String description) {
	}

	record WeightCategoryResponse(String name, int maxKg) {
	}

	record CategoryResponse(String name, String icon, List<String> types) {
	}

	record LocationResponse(String name, String kind) {
	}

	private final TypeRepository types;
	private final CategoryRepository categories;
	private final LocationRepository locations;
	private final EquipmentPoolRepository pools;
	private final WeightCategoryRepository weights;
	private final EquipmentRegistry registry;

	ReferenceController(TypeRepository types, CategoryRepository categories, LocationRepository locations,
			EquipmentPoolRepository pools, WeightCategoryRepository weights, EquipmentRegistry registry) {
		this.types = types;
		this.categories = categories;
		this.locations = locations;
		this.pools = pools;
		this.weights = weights;
		this.registry = registry;
	}

	@PostMapping("/equipment-types")
	@PreAuthorize("hasRole('MANAGER')")
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	TypeResponse createType(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody NewTypeRequest request) {
		return TypeResponse.of(registry.createType(user,
				new EquipmentRegistry.NewType(request.name(), request.category(), request.labelPrefix(), request.defaultLoanDays())));
	}

	// Lightest first
	@GetMapping("/weight-categories")
	List<WeightCategoryResponse> weightCategories() {
		return weights.findAllByOrderByMaxKgAsc().stream().map(w -> new WeightCategoryResponse(w.getName(), w.getMaxKg())).toList();
	}

	@GetMapping("/pools")
	List<PoolResponse> pools() {
		return pools.findAllByOrderBySortOrderAscNameAsc().stream()
				.map(p -> new PoolResponse(p.getCode(), p.getName(), p.getDescription()))
				.toList();
	}

	@GetMapping("/equipment-types")
	@Transactional(readOnly = true)
	List<TypeResponse> types() {
		return types.findAllByOrderBySortOrderAscNameAsc().stream().map(TypeResponse::of).toList();
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
