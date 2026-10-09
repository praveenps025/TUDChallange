package nhs.ot.equiptrack.equipment;

import jakarta.persistence.Converter;

import nhs.ot.equiptrack.common.LowercaseEnumConverter;

// Transitions are enforced in EquipmentLifecycle
public enum EquipmentStatus {
	AVAILABLE, ISSUED, INSPECT, MISSING;

	public String value() {
		return name().toLowerCase();
	}

	@Converter(autoApply = true)
	public static class DbConverter extends LowercaseEnumConverter<EquipmentStatus> {
		public DbConverter() {
			super(EquipmentStatus.class);
		}
	}

}
