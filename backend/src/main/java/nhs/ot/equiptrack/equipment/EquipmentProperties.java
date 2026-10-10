package nhs.ot.equiptrack.equipment;

import org.springframework.boot.context.properties.ConfigurationProperties;

// returnLocation: where returned items go (a location name, e.g. "OT store")
// homeLocation: the location of items a patient has taken home
@ConfigurationProperties("app.equipment")
public record EquipmentProperties(String returnLocation, String homeLocation) {
}
