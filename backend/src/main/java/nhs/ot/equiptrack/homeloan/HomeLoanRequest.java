package nhs.ot.equiptrack.homeloan;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Converter;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import nhs.ot.equiptrack.common.LowercaseEnumConverter;
import nhs.ot.equiptrack.discharge.Patient;
import nhs.ot.equiptrack.equipment.Equipment;
import nhs.ot.equiptrack.user.AppUser;

// Equipment a patient is to take home, each item with a return date.
// Staff request it; a manager approves (the loans then go home) or rejects it.
@Entity
public class HomeLoanRequest {

	public enum Status {
		REQUESTED, APPROVED, REJECTED;

		@Converter(autoApply = true)
		public static class DbConverter extends LowercaseEnumConverter<Status> {
			public DbConverter() {
				super(Status.class);
			}
		}
	}

	@Embeddable
	public static class Item {

		@ManyToOne(fetch = FetchType.LAZY)
		@JoinColumn(name = "equipment_id")
		private Equipment equipment;

		private LocalDate dueOn;

		protected Item() {
		}

		Item(Equipment equipment, LocalDate dueOn) {
			this.equipment = equipment;
			this.dueOn = dueOn;
		}

		public Equipment getEquipment() {
			return equipment;
		}

		public LocalDate getDueOn() {
			return dueOn;
		}
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	private Patient patient;

	private Status status = Status.REQUESTED;

	private String note;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "requested_by")
	private AppUser requestedBy;

	private Instant requestedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "decided_by")
	private AppUser decidedBy;

	private Instant decidedAt;

	private String decisionReason;

	@ElementCollection
	@CollectionTable(name = "home_loan_request_item", joinColumns = @JoinColumn(name = "request_id"))
	private List<Item> items = new ArrayList<>();

	protected HomeLoanRequest() {
	}

	HomeLoanRequest(Patient patient, String note, AppUser requestedBy, Instant requestedAt) {
		this.patient = patient;
		this.note = note;
		this.requestedBy = requestedBy;
		this.requestedAt = requestedAt;
	}

	void add(Equipment equipment, LocalDate dueOn) {
		items.add(new Item(equipment, dueOn));
	}

	void decide(boolean approve, AppUser manager, String reason, Instant now) {
		this.status = approve ? Status.APPROVED : Status.REJECTED;
		this.decidedBy = manager;
		this.decisionReason = reason;
		this.decidedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Patient getPatient() {
		return patient;
	}

	public Status getStatus() {
		return status;
	}

	public String getNote() {
		return note;
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

	public List<Item> getItems() {
		return items;
	}

}
