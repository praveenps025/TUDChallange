package nhs.ot.equiptrack.discharge;

import java.time.Instant;

import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import nhs.ot.equiptrack.common.LowercaseEnumConverter;

// A request to let a patient leave with equipment unaccounted for.
// Approval by a manager comes later; for now requests stay "requested".
@Entity
public class DischargeOverride {

	public enum Status {
		REQUESTED, APPROVED, REJECTED;

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
	private Patient patient;

	private String reason;

	private Status status = Status.REQUESTED;

	private Long requestedBy;

	private Instant requestedAt;

	protected DischargeOverride() {
	}

	DischargeOverride(Patient patient, String reason, long requestedBy, Instant requestedAt) {
		this.patient = patient;
		this.reason = reason;
		this.requestedBy = requestedBy;
		this.requestedAt = requestedAt;
	}

	public String getReason() {
		return reason;
	}

	public Status getStatus() {
		return status;
	}

}
