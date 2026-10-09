package nhs.ot.equiptrack.waitlist;

import java.time.Instant;

import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;

import nhs.ot.equiptrack.common.LowercaseEnumConverter;
import nhs.ot.equiptrack.discharge.Patient;
import nhs.ot.equiptrack.equipment.EquipmentType;
import nhs.ot.equiptrack.equipment.Location;

// A ward asking for a type of equipment, optionally for a named patient
@Entity
public class WaitlistRequest {

	public enum Status {
		OPEN, ALLOCATED, CANCELLED;

		@Converter(autoApply = true)
		public static class DbConverter extends LowercaseEnumConverter<Status> {
			public DbConverter() {
				super(Status.class);
			}
		}
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	private Location ward;

	@ManyToOne(fetch = FetchType.LAZY)
	private EquipmentType type;

	@ManyToOne(fetch = FetchType.LAZY)
	private Patient patient;

	private Status status = Status.OPEN;

	private Long requestedBy;

	private Instant requestedAt;

	private Long allocatedEquipmentId;

	private Long closedBy;

	private Instant closedAt;

	@Version
	private long version;

	protected WaitlistRequest() {
	}

	WaitlistRequest(Location ward, EquipmentType type, Patient patient, long requestedBy, Instant requestedAt) {
		this.ward = ward;
		this.type = type;
		this.patient = patient;
		this.requestedBy = requestedBy;
		this.requestedAt = requestedAt;
	}

	void cancel(long userId, Instant now) {
		this.status = Status.CANCELLED;
		this.closedBy = userId;
		this.closedAt = now;
	}

	public void allocate(long equipmentId, long userId, Instant now) {
		this.status = Status.ALLOCATED;
		this.allocatedEquipmentId = equipmentId;
		this.closedBy = userId;
		this.closedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Location getWard() {
		return ward;
	}

	public EquipmentType getType() {
		return type;
	}

	public Patient getPatient() {
		return patient;
	}

	public Status getStatus() {
		return status;
	}

	public Instant getRequestedAt() {
		return requestedAt;
	}

}
