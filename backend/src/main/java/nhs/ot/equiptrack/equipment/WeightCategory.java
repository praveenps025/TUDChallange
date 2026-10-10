package nhs.ot.equiptrack.equipment;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

// A patient weight band, e.g. Standard (up to 125 kg)
@Entity
public class WeightCategory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	private int maxKg;

	private int sortOrder;

	protected WeightCategory() {
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public int getMaxKg() {
		return maxKg;
	}

}
