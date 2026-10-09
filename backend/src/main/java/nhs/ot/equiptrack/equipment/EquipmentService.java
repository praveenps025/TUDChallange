package nhs.ot.equiptrack.equipment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nhs.ot.equiptrack.auth.CurrentUser;
import nhs.ot.equiptrack.common.ApiException;

@Service
public class EquipmentService {

	private final EquipmentRepository equipment;
	private final EquipmentLifecycle lifecycle;
	private final EquipmentViews views;

	EquipmentService(EquipmentRepository equipment, EquipmentLifecycle lifecycle, EquipmentViews views) {
		this.equipment = equipment;
		this.lifecycle = lifecycle;
		this.views = views;
	}

	@Transactional(readOnly = true)
	public EquipmentResponse get(String label) {
		return views.of(find(label));
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
