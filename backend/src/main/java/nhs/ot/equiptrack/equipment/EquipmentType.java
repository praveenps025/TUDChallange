package nhs.ot.equiptrack.equipment;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class EquipmentType {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	@ManyToOne(fetch = FetchType.LAZY)
	private EquipmentCategory category;

	private int defaultLoanDays;

	private int sortOrder;

	protected EquipmentType() {
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public EquipmentCategory getCategory() {
		return category;
	}

	public int getDefaultLoanDays() {
		return defaultLoanDays;
	}

	public int getSortOrder() {
		return sortOrder;
	}

}
