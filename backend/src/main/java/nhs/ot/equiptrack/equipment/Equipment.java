package nhs.ot.equiptrack.equipment;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
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

	// Details from registration (the old Excel tracker columns). Asset number,
	// serial number and model are searchable when a label is lost or damaged.
	private String assetNumber;

	private String serialNumber;

	private String model;

	private String assetFlag;

	private String size;

	private String maxUserWeight;

	private String hc21Number;

	private String dmlNumber;

	private String therapist;

	@Column(columnDefinition = "text")
	private String comments;

	private LocalDate purchaseDate;

	private LocalDate lastServiceDate;

	private Instant labelPrintedAt;

	@Version
	private long version;

	protected Equipment() {
	}

	// A newly registered item: in stock and ready to issue
	Equipment(String labelCode, EquipmentType type, EquipmentPool pool, Location location, EquipmentDetails details, Instant now) {
		this.labelCode = labelCode;
		this.type = type;
		this.pool = pool;
		this.location = location;
		this.status = EquipmentStatus.AVAILABLE;
		this.statusChangedAt = now;
		this.lastSeenAt = now;
		update(details, pool);
	}

	// Details only; status and location change through EquipmentLifecycle
	void update(EquipmentDetails d, EquipmentPool pool) {
		this.pool = pool;
		this.name = d.name().trim();
		this.model = trim(d.model());
		this.serialNumber = trim(d.serialNumber());
		this.assetNumber = trim(d.assetNumber());
		this.assetFlag = trim(d.assetFlag());
		this.size = trim(d.size());
		this.maxUserWeight = trim(d.maxUserWeight());
		this.hc21Number = trim(d.hc21Number());
		this.dmlNumber = trim(d.dmlNumber());
		this.therapist = trim(d.therapist());
		this.comments = trim(d.comments());
		this.purchaseDate = d.purchaseDate();
		this.lastServiceDate = d.lastServiceDate();
	}

	void labelPrinted(Instant now) {
		this.labelPrintedAt = now;
	}

	private static String trim(String value) {
		return value == null || value.isBlank() ? null : value.trim();
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

	public String getAssetNumber() {
		return assetNumber;
	}

	public String getSerialNumber() {
		return serialNumber;
	}

	public String getModel() {
		return model;
	}

	public String getAssetFlag() {
		return assetFlag;
	}

	public String getSize() {
		return size;
	}

	public String getMaxUserWeight() {
		return maxUserWeight;
	}

	public String getHc21Number() {
		return hc21Number;
	}

	public String getDmlNumber() {
		return dmlNumber;
	}

	public String getTherapist() {
		return therapist;
	}

	public String getComments() {
		return comments;
	}

	public LocalDate getPurchaseDate() {
		return purchaseDate;
	}

	public LocalDate getLastServiceDate() {
		return lastServiceDate;
	}

	public Instant getLabelPrintedAt() {
		return labelPrintedAt;
	}

}
