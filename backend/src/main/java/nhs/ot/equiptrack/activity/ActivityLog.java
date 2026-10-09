package nhs.ot.equiptrack.activity;

import java.time.Instant;

import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import nhs.ot.equiptrack.common.LowercaseEnumConverter;

@Entity
public class ActivityLog {

	// Also the icon shown next to the entry on Home
	public enum Kind {
		IN, OUT, CLIPBOARD;

		@Converter(autoApply = true)
		public static class DbConverter extends LowercaseEnumConverter<Kind> {
			public DbConverter() {
				super(Kind.class);
			}
		}
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Kind kind;

	private String title;

	private String detail;

	private Long userId;

	private Long equipmentId;

	private Instant occurredAt;

	protected ActivityLog() {
	}

	ActivityLog(Kind kind, String title, String detail, Long userId, Long equipmentId, Instant occurredAt) {
		this.kind = kind;
		this.title = title;
		this.detail = detail;
		this.userId = userId;
		this.equipmentId = equipmentId;
		this.occurredAt = occurredAt;
	}

	public Kind getKind() {
		return kind;
	}

	public String getTitle() {
		return title;
	}

	public String getDetail() {
		return detail;
	}

	public Long getUserId() {
		return userId;
	}

	public Long getEquipmentId() {
		return equipmentId;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

}
