package nhs.ot.equiptrack.common;

import java.util.Locale;

import jakarta.persistence.AttributeConverter;

// Stores enums as the lowercase words the schema and API use ("available", "open")
public abstract class LowercaseEnumConverter<E extends Enum<E>> implements AttributeConverter<E, String> {

	private final Class<E> type;

	protected LowercaseEnumConverter(Class<E> type) {
		this.type = type;
	}

	@Override
	public String convertToDatabaseColumn(E value) {
		return value == null ? null : value.name().toLowerCase(Locale.ROOT);
	}

	@Override
	public E convertToEntityAttribute(String value) {
		return value == null ? null : Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
	}

}
