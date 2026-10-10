package nhs.ot.equiptrack.equipment;

// The equipment object from the API contract. "id" is the scanned label code.
// homeLoan is true while a patient has the item at home.
public record EquipmentResponse(String id, String name, String type, String category, String status, String location,
		String note, boolean homeLoan) {
}
