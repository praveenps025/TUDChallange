package nhs.ot.equiptrack.discharge;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import nhs.ot.equiptrack.equipment.Location;

// Only the minimum is kept: a keyed hash of the full MRN (unique per patient)
// and its last four digits, which are all the API ever returns.
@Entity
public class Patient {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(length = 64)
	@JdbcTypeCode(SqlTypes.CHAR)
	private String mrnHash;

	@Column(length = 4)
	@JdbcTypeCode(SqlTypes.CHAR)
	private String mrnSuffix;

	@ManyToOne(fetch = FetchType.LAZY)
	private Location ward;

	private String bed;

	private LocalDate dischargePlannedOn;

	protected Patient() {
	}

	public Patient(String mrnHash, String mrnSuffix, Location ward, String bed) {
		this.mrnHash = mrnHash;
		this.mrnSuffix = mrnSuffix;
		this.ward = ward;
		this.bed = bed;
	}

	public Long getId() {
		return id;
	}

	public String getMrnSuffix() {
		return mrnSuffix;
	}

	public Location getWard() {
		return ward;
	}

	public String getBed() {
		return bed;
	}

	public LocalDate getDischargePlannedOn() {
		return dischargePlannedOn;
	}

}
