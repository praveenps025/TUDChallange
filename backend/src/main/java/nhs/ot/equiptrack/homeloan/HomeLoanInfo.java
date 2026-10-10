package nhs.ot.equiptrack.homeloan;

import java.time.LocalDate;
import java.util.List;

import nhs.ot.equiptrack.common.Dates;
import nhs.ot.equiptrack.user.AppUser;

// A home loan request as the API shows it (on the discharge check and in approvals)
public record HomeLoanInfo(long id, String status, String mrn, String ward, String bed, String note, String requestedBy,
		String requestedAt, String decidedBy, String decisionReason, List<Item> items) {

	public record Item(String id, String name, LocalDate dueOn) {
	}

	public static HomeLoanInfo of(HomeLoanRequest r, Dates dates) {
		var patient = r.getPatient();
		return new HomeLoanInfo(r.getId(), r.getStatus().name().toLowerCase(), patient.getMrnSuffix(),
				patient.getWard() == null ? null : patient.getWard().getName(), patient.getBed(), r.getNote(),
				name(r.getRequestedBy()), dates.time(r.getRequestedAt()), name(r.getDecidedBy()), r.getDecisionReason(),
				r.getItems().stream()
						.map(i -> new Item(i.getEquipment().getLabelCode(), i.getEquipment().getName(), i.getDueOn()))
						.toList());
	}

	private static String name(AppUser user) {
		return user == null ? null : user.getDisplayName();
	}

}
