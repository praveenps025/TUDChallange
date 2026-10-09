package nhs.ot.equiptrack.discharge;

import java.time.Instant;

import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import nhs.ot.equiptrack.common.LowercaseEnumConverter;
import nhs.ot.equiptrack.user.AppUser;

// Letting a patient leave with equipment unaccounted for. Staff request it
// with a reason; a manager approves or rejects it, also with a reason.
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

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "requested_by")
	private AppUser requestedBy;

	private Instant requestedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "decided_by")
	private AppUser decidedBy;

	private Instant decidedAt;

	private String decisionReason;

	protected DischargeOverride() {
	}

	DischargeOverride(Patient patient, String reason, AppUser requestedBy, Instant requestedAt) {
		this.patient = patient;
		this.reason = reason;
		this.requestedBy = requestedBy;
		this.requestedAt = requestedAt;
	}

	void decide(boolean approve, AppUser manager, String decisionReason, Instant now) {
		this.status = approve ? Status.APPROVED : Status.REJECTED;
		this.decidedBy = manager;
		this.decisionReason = decisionReason;
		this.decidedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Patient getPatient() {
		return patient;
	}

	public String getReason() {
		return reason;
	}

	public Status getStatus() {
		return status;
	}

	public AppUser getRequestedBy() {
		return requestedBy;
	}

	public Instant getRequestedAt() {
		return requestedAt;
	}

	public AppUser getDecidedBy() {
		return decidedBy;
	}

	public String getDecisionReason() {
		return decisionReason;
	}

}
