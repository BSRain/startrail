package com.startrail.app.astro;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/** Time range and local-day calculations used by the timeline UI. */
public final class TimeAxis {
    public static final long MIN_MILLIS = Instant.parse("1900-01-01T00:00:00Z").toEpochMilli();
    public static final long MAX_MILLIS = Instant.parse("2100-12-31T23:59:59.999Z").toEpochMilli();

    private TimeAxis() {}

    public static long clamp(long millis) {
        return Math.max(MIN_MILLIS, Math.min(MAX_MILLIS, millis));
    }

    public static long advance(long time, long elapsedMillis, int rate, int direction) {
        if (elapsedMillis < 0) {
            throw new IllegalArgumentException("elapsedMillis must be non-negative");
        }
        if (rate != 60 && rate != 600 && rate != 3600) {
            throw new IllegalArgumentException("rate must be 60, 600, or 3600");
        }
        if (direction != -1 && direction != 1) {
            throw new IllegalArgumentException("direction must be -1 or 1");
        }

        long delta;
        try {
            delta = Math.multiplyExact(elapsedMillis, (long) rate);
            if (direction < 0) {
                delta = Math.negateExact(delta);
            }
            return clamp(Math.addExact(clamp(time), delta));
        } catch (ArithmeticException overflow) {
            return direction > 0 ? MAX_MILLIS : MIN_MILLIS;
        }
    }

    public static DayWindow day(Instant time, ZoneId zone) {
        Objects.requireNonNull(time, "time");
        Objects.requireNonNull(zone, "zone");
        LocalDate localDate = time.atZone(zone).toLocalDate();
        long start = localDate.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = localDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        long durationMillis = end - start;
        return new DayWindow(start, end, Math.toIntExact(durationMillis / 60_000L));
    }

    public static final class DayWindow {
        public final long startMillis;
        public final long endMillis;
        public final int minutes;

        private DayWindow(long startMillis, long endMillis, int minutes) {
            if (endMillis <= startMillis || minutes <= 0) {
                throw new IllegalArgumentException("day window must have a positive duration");
            }
            this.startMillis = startMillis;
            this.endMillis = endMillis;
            this.minutes = minutes;
        }

        public int progress(long millis) {
            long bounded = Math.max(startMillis, Math.min(endMillis, millis));
            return (int) ((bounded - startMillis) / 60_000L);
        }

        public long atMinute(int minute) {
            int bounded = Math.max(0, Math.min(minutes, minute));
            return clamp(startMillis + bounded * 60_000L);
        }
    }
}
