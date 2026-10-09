package nhs.ot.equiptrack.equipment;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import nhs.ot.equiptrack.common.Dates;

// Builds EquipmentResponse. The note is worked out from the item's state each
// time ("Loaned 28 Sep · Due today"), so it never goes stale.
@Component
public class EquipmentViews {

	private final LoanRepository loans;
	private final Dates dates;

	EquipmentViews(LoanRepository loans, Dates dates) {
		this.loans = loans;
		this.dates = dates;
	}

	public EquipmentResponse of(Equipment equipment) {
		return of(List.of(equipment)).getFirst();
	}

	public List<EquipmentResponse> of(List<Equipment> items) {
		Map<Long, Loan> openLoans = loans.findOpenFor(items.stream().map(Equipment::getId).toList()).stream()
				.collect(Collectors.toMap(l -> l.getEquipment().getId(), Function.identity()));
		return items.stream().map(e -> toResponse(e, openLoans.get(e.getId()))).toList();
	}

	private EquipmentResponse toResponse(Equipment e, Loan openLoan) {
		return new EquipmentResponse(e.getLabelCode(), e.getName(), e.getType().getName(),
				e.getType().getCategory().getName(), e.getStatus().value(), e.getLocation().getName(), note(e, openLoan));
	}

	private String note(Equipment e, Loan loan) {
		return switch (e.getStatus()) {
			case ISSUED -> loan == null ? "On loan" : "Loaned " + dates.day(loan.getIssuedAt()) + " · " + due(loan);
			case INSPECT -> "Returned " + dates.day(e.getStatusChangedAt()) + " · Needs inspection";
			case AVAILABLE -> "Checked " + dates.day(e.getStatusChangedAt());
			case MISSING -> "Last seen " + dates.day(e.getLastSeenAt() != null ? e.getLastSeenAt() : e.getStatusChangedAt());
		};
	}

	public String due(Loan loan) {
		return loan.isOverdue(dates.now()) ? "Overdue since " + dates.day(loan.getDueAt()) : "Due " + dates.day(loan.getDueAt());
	}

}
