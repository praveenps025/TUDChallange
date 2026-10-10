package nhs.ot.equiptrack.equipment;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import nhs.ot.equiptrack.discharge.Patient;

// One period an item spends on a ward. Open while returnedAt is null.
@Entity
public class Loan {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	private Equipment equipment;

	@ManyToOne(fetch = FetchType.LAZY)
	private Patient patient;

	@ManyToOne(fetch = FetchType.LAZY)
	private Location ward;

	private Long waitlistRequestId;

	private Long issuedBy;

	private Instant issuedAt;

	private Instant dueAt;

	private Instant returnedAt;

	private Long returnedBy;

	private Boolean returnedClean;

	// Set when the patient took the item home (a manager approved the home loan)
	private Instant homeSince;

	private String homeNote;

	protected Loan() {
	}

	Loan(Equipment equipment, Patient patient, Location ward, Long waitlistRequestId, long issuedBy, Instant issuedAt, Instant dueAt) {
		this.equipment = equipment;
		this.patient = patient;
		this.ward = ward;
		this.waitlistRequestId = waitlistRequestId;
		this.issuedBy = issuedBy;
		this.issuedAt = issuedAt;
		this.dueAt = dueAt;
	}

	void close(long returnedBy, Instant returnedAt, boolean clean) {
		this.returnedBy = returnedBy;
		this.returnedAt = returnedAt;
		this.returnedClean = clean;
	}

	void goHome(Instant now, Instant dueAt, String note) {
		this.homeSince = now;
		this.dueAt = dueAt;
		this.homeNote = note;
	}

	public boolean isHome() {
		return homeSince != null;
	}

	public Instant getHomeSince() {
		return homeSince;
	}

	public String getHomeNote() {
		return homeNote;
	}

	public boolean isOverdue(Instant now) {
		return returnedAt == null && dueAt.isBefore(now);
	}

	public Long getId() {
		return id;
	}

	public Equipment getEquipment() {
		return equipment;
	}

	public Patient getPatient() {
		return patient;
	}

	public Location getWard() {
		return ward;
	}

	public Instant getIssuedAt() {
		return issuedAt;
	}

	public Instant getDueAt() {
		return dueAt;
	}

	public Instant getReturnedAt() {
		return returnedAt;
	}

}
