package nhs.ot.equiptrack.equipment;

import org.springframework.boot.context.properties.ConfigurationProperties;

// returnLocation: where returned items go (a location name, e.g. "OT store")
@ConfigurationProperties("app.equipment")
public record EquipmentProperties(String returnLocation) {
}
