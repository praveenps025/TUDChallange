package nhs.ot.equiptrack.waitlist;

import java.time.Instant;
import java.util.Comparator;

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

	// Chosen by whoever makes the request, from the patient's condition
	public enum Priority {
		URGENT, SOON, ROUTINE;

		@Converter(autoApply = true)
		public static class DbConverter extends LowercaseEnumConverter<Priority> {
			public DbConverter() {
				super(Priority.class);
			}
		}
	}

	// The order requests are served in: most urgent first, then longest waiting
	public static final Comparator<WaitlistRequest> QUEUE_ORDER = Comparator
			.comparing(WaitlistRequest::getPriority)
			.thenComparing(WaitlistRequest::getRequestedAt)
			.thenComparing(WaitlistRequest::getId);

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

	private Priority priority = Priority.ROUTINE;

	private String note;

	private Long requestedBy;

	private Instant requestedAt;

	private Long allocatedEquipmentId;

	private Long closedBy;

	private Instant closedAt;

	@Version
	private long version;

	protected WaitlistRequest() {
	}

	WaitlistRequest(Location ward, EquipmentType type, Patient patient, Priority priority, String note, long requestedBy,
			Instant requestedAt) {
		this.ward = ward;
		this.type = type;
		this.patient = patient;
		this.priority = priority;
		this.note = note;
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

	public Priority getPriority() {
		return priority;
	}

	public String getNote() {
		return note;
	}

}
