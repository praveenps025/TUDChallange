package nhs.ot.equiptrack.insights;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nhs.ot.equiptrack.common.Dates;
import nhs.ot.equiptrack.equipment.CategoryRepository;
import nhs.ot.equiptrack.equipment.EquipmentRepository;
import nhs.ot.equiptrack.equipment.EquipmentStatus;
import nhs.ot.equiptrack.equipment.LoanRepository;
import nhs.ot.equiptrack.waitlist.WaitlistRepository;
import nhs.ot.equiptrack.waitlist.WaitlistService;

@Service
public class InsightsService {

	// onTime is null when nothing has been returned yet this month
	public record Overview(String month, long tracked, long unaccounted, long waitlists, long readyToAllocate, Integer onTime,
			List<CategoryRow> categories) {
	}

	// check = needs inspection or missing; advice may be null
	public record CategoryRow(String name, long available, long issued, long check, long waiting, String advice) {
	}

	private final EquipmentRepository equipment;
	private final CategoryRepository categories;
	private final LoanRepository loans;
	private final WaitlistRepository requests;
	private final WaitlistService waitlist;
	private final Dates dates;

	InsightsService(EquipmentRepository equipment, CategoryRepository categories, LoanRepository loans,
			WaitlistRepository requests, WaitlistService waitlist, Dates dates) {
		this.equipment = equipment;
		this.categories = categories;
		this.loans = loans;
		this.requests = requests;
		this.waitlist = waitlist;
		this.dates = dates;
	}

	@Transactional(readOnly = true)
	public Overview overview() {
		LocalDate today = dates.today();
		List<WaitlistService.Entry> open = waitlist.list();

		Map<Long, Map<EquipmentStatus, Long>> byCategory = new HashMap<>();
		equipment.countByCategoryAndStatus().forEach(row -> byCategory
				.computeIfAbsent(row.getCategoryId(), id -> new HashMap<>())
				.put(row.getStatus(), row.getTotal()));
		Map<Long, Long> waitingByCategory = new HashMap<>();
		requests.findOpenOldestFirst().forEach(r -> waitingByCategory.merge(r.getType().getCategory().getId(), 1L, Long::sum));

		List<CategoryRow> rows = categories.findAllByOrderBySortOrderAscNameAsc().stream().map(category -> {
			Map<EquipmentStatus, Long> counts = byCategory.getOrDefault(category.getId(), Map.of());
			long available = counts.getOrDefault(EquipmentStatus.AVAILABLE, 0L);
			long waiting = waitingByCategory.getOrDefault(category.getId(), 0L);
			return new CategoryRow(category.getName(), available, counts.getOrDefault(EquipmentStatus.ISSUED, 0L),
					counts.getOrDefault(EquipmentStatus.INSPECT, 0L) + counts.getOrDefault(EquipmentStatus.MISSING, 0L),
					waiting, advice(available, waiting));
		}).toList();

		var monthStart = dates.startOf(today.withDayOfMonth(1));
		long returned = loans.countReturnedSince(monthStart);
		Integer onTime = returned == 0 ? null
				: (int) Math.round(100.0 * loans.countReturnedOnTimeSince(monthStart) / returned);

		return new Overview(today.getMonth().getDisplayName(TextStyle.FULL, Locale.UK), equipment.count(),
				equipment.countByStatus(EquipmentStatus.MISSING), open.size(),
				open.stream().filter(WaitlistService.Entry::availableNow).count(), onTime, rows);
	}

	// More requests than stock suggests buying; otherwise move stock to where it is wanted
	static String advice(long available, long waiting) {
		if (waiting > available) {
			return "Consider buying";
		}
		return waiting > 0 ? "Redistribute" : null;
	}

}
