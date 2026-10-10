package nhs.ot.equiptrack.common;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// "Today", due dates and display times in the hospital's time zone (app.timezone)
@Component
public class Dates {

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

	private final Clock clock;
	private final ZoneId zone;

	public Dates(Clock clock, @Value("${app.timezone}") ZoneId zone) {
		this.clock = clock;
		this.zone = zone;
	}

	public Instant now() {
		return clock.instant();
	}

	public LocalDate today() {
		return LocalDate.now(clock.withZone(zone));
	}

	public LocalDate dateOf(Instant instant) {
		return instant.atZone(zone).toLocalDate();
	}

	public Instant startOf(LocalDate date) {
		return date.atStartOfDay(zone).toInstant();
	}

	// A loan due on a date runs until the end of that day
	public Instant endOf(LocalDate date) {
		// 23:59:59, not LocalTime.MAX: Postgres keeps microseconds and would round .999999999 up to midnight
		return date.atTime(23, 59, 59).atZone(zone).toInstant();
	}

	public long daysSince(Instant instant) {
		return ChronoUnit.DAYS.between(dateOf(instant), today());
	}

	// "today", "yesterday", "tomorrow" or "6 Oct"
	public String day(Instant instant) {
		LocalDate date = dateOf(instant);
		long offset = ChronoUnit.DAYS.between(today(), date);
		if (offset == 0) {
			return "today";
		}
		if (offset == -1) {
			return "yesterday";
		}
		if (offset == 1) {
			return "tomorrow";
		}
		return DAY.format(date);
	}

	// "09:32" for today, otherwise the day
	public String time(Instant instant) {
		return dateOf(instant).equals(today()) ? TIME.format(instant.atZone(zone)) : day(instant);
	}

	public String clockTime(Instant instant) {
		return TIME.format(instant.atZone(zone));
	}

}
