package nhs.ot.equiptrack.audit;

import java.time.Instant;

import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;

import nhs.ot.equiptrack.common.LowercaseEnumConverter;
import nhs.ot.equiptrack.user.AppUser;

// A walk round an area checking that the items expected there are present.
// The expected items are captured in audit_walk_item when the walk starts.
@Entity
public class AuditWalk {

	public enum Status {
		OPEN, CLOSED;

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

	private String area;

	private Status status = Status.OPEN;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "started_by")
	private AppUser startedBy;

	private Instant startedAt;

	private Long closedBy;

	private Instant closedAt;

	@Version
	private long version;

	protected AuditWalk() {
	}

	public Long getId() {
		return id;
	}

	public String getArea() {
		return area;
	}

	public Status getStatus() {
		return status;
	}

	public AppUser getStartedBy() {
		return startedBy;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

}
