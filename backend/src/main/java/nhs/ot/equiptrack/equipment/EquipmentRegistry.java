package nhs.ot.equiptrack.equipment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nhs.ot.equiptrack.activity.ActivityLog;
import nhs.ot.equiptrack.activity.ActivityService;
import nhs.ot.equiptrack.auth.CurrentUser;
import nhs.ot.equiptrack.common.ApiException;
import nhs.ot.equiptrack.common.Dates;

// Adding equipment to the inventory (managers only, enforced in the controller):
// registering items, editing their details, logging label prints and adding types.
@Service
public class EquipmentRegistry {

	// Everything about one item, for the inventory and label screens
	public record DetailsResponse(String id, String name, String type, String category, String pool, String poolName,
			String status, String location, String note, String model, String serialNumber, String assetNumber,
			String assetFlag, String size, String maxUserWeight, String hc21Number, String dmlNumber, String therapist,
			String comments, LocalDate purchaseDate, LocalDate lastServiceDate, Instant labelPrintedAt, String weightCategory,
			Integer weightMaxKg) {
	}

	public record NewType(String name, String category, String labelPrefix, Integer defaultLoanDays) {
	}

	private static final int DEFAULT_LOAN_DAYS = 14;

	private final EquipmentRepository equipment;
	private final EquipmentService equipmentService;
	private final TypeRepository types;
	private final CategoryRepository categories;
	private final EquipmentPoolRepository pools;
	private final LocationRepository locations;
	private final WeightCategoryRepository weights;
	private final EquipmentViews views;
	private final ActivityService activity;
	private final EquipmentProperties props;
	private final Dates dates;

	EquipmentRegistry(EquipmentRepository equipment, EquipmentService equipmentService, TypeRepository types,
			CategoryRepository categories, EquipmentPoolRepository pools, LocationRepository locations,
			WeightCategoryRepository weights, EquipmentViews views, ActivityService activity, EquipmentProperties props, Dates dates) {
		this.equipment = equipment;
		this.equipmentService = equipmentService;
		this.types = types;
		this.categories = categories;
		this.pools = pools;
		this.locations = locations;
		this.weights = weights;
		this.views = views;
		this.activity = activity;
		this.props = props;
		this.dates = dates;
	}

	@Transactional(readOnly = true)
	public DetailsResponse details(String label) {
		return toDetails(equipmentService.find(label));
	}

	// The new item gets the next number for its type's prefix, e.g. WC-032
	@Transactional
	public DetailsResponse register(CurrentUser user, String typeName, String poolCode, String locationName, EquipmentDetails details) {
		EquipmentType type = types.findByNameForUpdate(required(typeName, "Choose an equipment type"))
				.orElseThrow(() -> ApiException.badRequest("Unknown equipment type " + typeName));
		EquipmentPool pool = pool(poolCode);
		String where = locationName == null || locationName.isBlank() ? props.returnLocation() : locationName.trim();
		Location location = locations.findByName(where).filter(Location::isActive)
				.orElseThrow(() -> ApiException.badRequest("Unknown location " + where));

		int next = equipment.findHighestNumber(type.getLabelPrefix()) + 1;
		String label = type.getLabelPrefix() + "-" + String.format("%03d", next);
		Equipment item = equipment.save(new Equipment(label, type, pool, location, weight(details.weightCategory()), details, dates.now()));
		activity.log(ActivityLog.Kind.IN, item.getName() + " registered", label + " · " + location.getName(), user, item.getId());
		return toDetails(item);
	}

	@Transactional
	public DetailsResponse update(CurrentUser user, String label, String poolCode, EquipmentDetails details) {
		Equipment item = equipmentService.find(label);
		item.update(details, pool(poolCode), weight(details.weightCategory()));
		activity.log(ActivityLog.Kind.CLIPBOARD, item.getName() + " details updated", item.getLabelCode(), user, item.getId());
		return toDetails(item);
	}

	@Transactional
	public DetailsResponse labelPrinted(CurrentUser user, String label) {
		Equipment item = equipmentService.find(label);
		boolean reprint = item.getLabelPrintedAt() != null;
		item.labelPrinted(dates.now());
		activity.log(ActivityLog.Kind.CLIPBOARD, reprint ? "Label reprinted" : "Label printed", item.getLabelCode(), user, item.getId());
		return toDetails(item);
	}

	// A category that doesn't exist yet is created with it
	@Transactional
	public EquipmentType createType(CurrentUser user, NewType input) {
		String name = required(input.name(), "Enter a name for the type");
		String prefix = required(input.labelPrefix(), "Enter a label prefix").toUpperCase(Locale.ROOT);
		if (!prefix.matches("[A-Z0-9]{1,10}")) {
			throw ApiException.badRequest("Label prefixes are 1 to 10 letters or numbers, e.g. WC");
		}
		if (types.findByName(name).isPresent()) {
			throw ApiException.conflict("There is already a type called " + name);
		}
		if (types.findByLabelPrefix(prefix).isPresent()) {
			throw ApiException.conflict("The prefix " + prefix + " is already used by another type");
		}
		String categoryName = required(input.category(), "Choose or enter a category");
		EquipmentCategory category = categories.findByNameIgnoreCase(categoryName)
				.orElseGet(() -> categories.save(new EquipmentCategory(categoryName, "layers")));
		int loanDays = input.defaultLoanDays() == null ? DEFAULT_LOAN_DAYS : input.defaultLoanDays();
		if (loanDays < 1 || loanDays > 365) {
			throw ApiException.badRequest("Loan length must be between 1 and 365 days");
		}
		EquipmentType type = types.save(new EquipmentType(name, category, prefix, loanDays));
		activity.log(ActivityLog.Kind.CLIPBOARD, "Equipment type added", name + " · " + prefix, user, null);
		return type;
	}

	private WeightCategory weight(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		return weights.findByNameIgnoreCase(name.trim()).orElseThrow(() -> ApiException.badRequest("Unknown weight category " + name));
	}

	private EquipmentPool pool(String code) {
		return pools.findByCodeIgnoreCase(required(code, "Choose a funding pool"))
				.orElseThrow(() -> ApiException.badRequest("Unknown funding pool " + code));
	}

	private DetailsResponse toDetails(Equipment e) {
		EquipmentResponse view = views.of(e);
		return new DetailsResponse(view.id(), view.name(), view.type(), view.category(), e.getPool().getCode(),
				e.getPool().getName(), view.status(), view.location(), view.note(), e.getModel(), e.getSerialNumber(),
				e.getAssetNumber(), e.getAssetFlag(), e.getSize(), e.getMaxUserWeight(), e.getHc21Number(), e.getDmlNumber(),
				e.getTherapist(), e.getComments(), e.getPurchaseDate(), e.getLastServiceDate(), e.getLabelPrintedAt(),
				e.getWeightCategory() == null ? null : e.getWeightCategory().getName(),
				e.getWeightCategory() == null ? null : e.getWeightCategory().getMaxKg());
	}

	private static String required(String value, String message) {
		if (value == null || value.isBlank()) {
			throw ApiException.badRequest(message);
		}
		return value.trim();
	}

}
