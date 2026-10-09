package nhs.ot.equiptrack.equipment;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class EquipmentCategory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	private String iconKey;

	private int sortOrder;

	protected EquipmentCategory() {
	}

	EquipmentCategory(String name, String iconKey) {
		this.name = name;
		this.iconKey = iconKey;
		this.sortOrder = 100;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getIconKey() {
		return iconKey;
	}

	public int getSortOrder() {
		return sortOrder;
	}

}
