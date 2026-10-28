package br.com.odontosaas.helpdesk.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

public final class SupportHours {

    public static final LocalTime OPENS = LocalTime.of(8, 0);
    public static final LocalTime CLOSES = LocalTime.of(18, 0);
    public static final String LABEL = "segunda a sexta, das 8h às 18h";

    private SupportHours() {}

    public static long slaMinutes(String priority) {
        return switch (priority) {
            case "URGENT" -> 120;
            case "HIGH" -> 480;
            case "NORMAL" -> 600;
            default -> 1800;
        };
    }

    public static String priorityFromImpact(String impact) {
        return switch (impact) {
            case "BLOCKING" -> "URGENT";
            case "DEGRADED" -> "HIGH";
            case "QUESTION" -> "NORMAL";
            default -> "LOW";
        };
    }

    public static Instant addBusinessMinutes(Instant start, long minutes, ZoneId zone) {
        ZonedDateTime cursor = nextOpenMoment(start.atZone(zone).truncatedTo(ChronoUnit.MINUTES));
        long remaining = minutes;
        while (true) {
            ZonedDateTime closing = cursor.with(CLOSES);
            long available = ChronoUnit.MINUTES.between(cursor, closing);
            if (remaining <= available) return cursor.plusMinutes(remaining).toInstant();
            remaining -= available;
            cursor = nextOpenMoment(
                    cursor.toLocalDate().plusDays(1).atTime(OPENS).atZone(zone));
        }
    }

    static ZonedDateTime nextOpenMoment(ZonedDateTime moment) {
        ZonedDateTime cursor = moment;
        while (true) {
            if (isWeekend(cursor.getDayOfWeek())) {
                cursor = cursor.toLocalDate().plusDays(1).atTime(OPENS).atZone(cursor.getZone());
            } else if (cursor.toLocalTime().isBefore(OPENS)) {
                return cursor.with(OPENS);
            } else if (!cursor.toLocalTime().isBefore(CLOSES)) {
                cursor = cursor.toLocalDate().plusDays(1).atTime(OPENS).atZone(cursor.getZone());
            } else {
                return cursor;
            }
        }
    }

    private static boolean isWeekend(DayOfWeek day) {
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
