package nhs.ot.equiptrack.insights;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import nhs.ot.equiptrack.common.ApiException;
import nhs.ot.equiptrack.common.Dates;
import nhs.ot.equiptrack.equipment.CategoryRepository;
import nhs.ot.equiptrack.equipment.EquipmentRepository;
import nhs.ot.equiptrack.equipment.EquipmentStatus;
import nhs.ot.equiptrack.equipment.LoanRepository;
import nhs.ot.equiptrack.waitlist.WaitlistRepository;
import nhs.ot.equiptrack.waitlist.WaitlistService;

@Service
public class InsightsService {

	// tracked, unaccounted, waitlists, readyToAllocate and categories are as of now;
	// issued, returned and onTime cover the chosen period. month is the period's
	// label ("October", "September", "2026"); onTime is null when nothing was returned.
	public record Overview(String period, String month, long tracked, long unaccounted, long waitlists, long readyToAllocate,
			long issued, long returned, Integer onTime, List<CategoryRow> categories) {
	}

	public enum Period {
		THIS_MONTH, LAST_MONTH, THIS_YEAR;

		static Period parse(String value) {
			try {
				return value == null || value.isBlank() ? THIS_MONTH : valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
			} catch (IllegalArgumentException e) {
				throw ApiException.badRequest("Period must be this-month, last-month or this-year");
			}
		}

		String value() {
			return name().toLowerCase(Locale.ROOT).replace('_', '-');
		}
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
	public Overview overview(String periodName) {
		Period period = Period.parse(periodName);
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

		LocalDate first = switch (period) {
			case THIS_MONTH -> today.withDayOfMonth(1);
			case LAST_MONTH -> today.withDayOfMonth(1).minusMonths(1);
			case THIS_YEAR -> today.withDayOfYear(1);
		};
		Instant from = dates.startOf(first);
		Instant to = dates.startOf(period == Period.LAST_MONTH ? today.withDayOfMonth(1) : today.plusDays(1));
		String label = period == Period.THIS_YEAR ? String.valueOf(first.getYear())
				: first.getMonth().getDisplayName(TextStyle.FULL, Locale.UK);

		long returned = loans.countReturnedBetween(from, to);
		Integer onTime = returned == 0 ? null
				: (int) Math.round(100.0 * loans.countReturnedOnTimeBetween(from, to) / returned);

		return new Overview(period.value(), label, equipment.count(), equipment.countByStatus(EquipmentStatus.MISSING), open.size(),
				open.stream().filter(WaitlistService.Entry::availableNow).count(), loans.countIssuedBetween(from, to), returned,
				onTime, rows);
	}

	// More requests than stock suggests buying; otherwise move stock to where it is wanted
	static String advice(long available, long waiting) {
		if (waiting > available) {
			return "Consider buying";
		}
		return waiting > 0 ? "Redistribute" : null;
	}

}
