package nhs.ot.equiptrack.equipment;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;

// Status and location only change through EquipmentLifecycle
@Entity
public class Equipment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String labelCode;

	private String name;

	@ManyToOne(fetch = FetchType.LAZY)
	private EquipmentType type;

	@ManyToOne(fetch = FetchType.LAZY)
	private EquipmentPool pool;

	private EquipmentStatus status;

	@ManyToOne(fetch = FetchType.LAZY)
	private Location location;

	private String note;

	private Instant lastSeenAt;

	private Instant statusChangedAt;

	// Tracker details, searchable when a label is lost or damaged
	private String assetNumber;

	private String serialNumber;

	private String model;

	@Version
	private long version;

	protected Equipment() {
	}

	void moveTo(EquipmentStatus status, Location location, Instant now) {
		this.status = status;
		this.location = location;
		this.statusChangedAt = now;
	}

	void seen(Instant now) {
		this.lastSeenAt = now;
	}

	public Long getId() {
		return id;
	}

	public String getLabelCode() {
		return labelCode;
	}

	public String getName() {
		return name;
	}

	public EquipmentType getType() {
		return type;
	}

	public EquipmentPool getPool() {
		return pool;
	}

	public EquipmentStatus getStatus() {
		return status;
	}

	public Location getLocation() {
		return location;
	}

	public String getNote() {
		return note;
	}

	public Instant getLastSeenAt() {
		return lastSeenAt;
	}

	public Instant getStatusChangedAt() {
		return statusChangedAt;
	}

}
