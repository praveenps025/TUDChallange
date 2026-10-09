package nhs.ot.equiptrack.equipment;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// The details a manager enters when registering or editing an item
public record EquipmentDetails(
		@NotBlank(message = "Enter a name for the item") @Size(max = 100, message = "Name is too long") String name,
		@Size(max = 100, message = "Model is too long") String model,
		@Size(max = 100, message = "Serial number is too long") String serialNumber,
		@Size(max = 50, message = "Asset number is too long") String assetNumber,
		@Pattern(regexp = "^(AT\\+|AT-)?$", message = "Asset flag must be AT+ or AT-") String assetFlag,
		@Size(max = 50, message = "Size is too long") String size,
		@Size(max = 30, message = "Maximum user weight is too long") String maxUserWeight,
		@Size(max = 50, message = "HC21 number is too long") String hc21Number,
		@Size(max = 50, message = "DML number is too long") String dmlNumber,
		@Size(max = 100, message = "Therapist is too long") String therapist,
		@Size(max = 2000, message = "Comments are too long") String comments,
		@PastOrPresent(message = "Purchase date can't be in the future") LocalDate purchaseDate,
		@PastOrPresent(message = "Last service date can't be in the future") LocalDate lastServiceDate) {
}
