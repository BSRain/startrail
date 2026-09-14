package com.startrail.app.astro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.Test;

public final class TimeAxisTest {
    @Test public void clampAndAdvanceSaturateAtBothEndsAndOnOverflow() {
        assertEquals(TimeAxis.MIN_MILLIS, TimeAxis.clamp(Long.MIN_VALUE));
        assertEquals(TimeAxis.MAX_MILLIS, TimeAxis.clamp(Long.MAX_VALUE));
        assertEquals(TimeAxis.MAX_MILLIS, TimeAxis.advance(TimeAxis.MAX_MILLIS - 1, Long.MAX_VALUE, 3600, 1));
        assertEquals(TimeAxis.MIN_MILLIS, TimeAxis.advance(TimeAxis.MIN_MILLIS + 1, Long.MAX_VALUE, 3600, -1));
        assertEquals(TimeAxis.MAX_MILLIS, TimeAxis.advance(Long.MAX_VALUE, 0, 60, 1));
    }

    @Test public void validRatesAndDirectionsProduceExactMilliseconds() {
        for (int rate : new int[]{60, 600, 3600}) {
            assertEquals(1_000_000L + 7L * rate, TimeAxis.advance(1_000_000L, 7, rate, 1));
            assertEquals(1_000_000L - 7L * rate, TimeAxis.advance(1_000_000L, 7, rate, -1));
        }
    }

    @Test public void daylightSavingDaysHaveTwentyThreeAndTwentyFiveHours() {
        ZoneId newYork = ZoneId.of("America/New_York");
        TimeAxis.DayWindow spring = TimeAxis.day(Instant.parse("2024-03-10T12:00:00Z"), newYork);
        assertEquals(23 * 60, spring.minutes);
        assertEquals(Instant.parse("2024-03-10T05:00:00Z").toEpochMilli(), spring.startMillis);
        assertEquals(Instant.parse("2024-03-11T04:00:00Z").toEpochMilli(), spring.endMillis);
        TimeAxis.DayWindow fall = TimeAxis.day(Instant.parse("2024-11-03T12:00:00Z"), newYork);
        assertEquals(25 * 60, fall.minutes);
        assertEquals(Instant.parse("2024-11-03T04:00:00Z").toEpochMilli(), fall.startMillis);
        assertEquals(Instant.parse("2024-11-04T05:00:00Z").toEpochMilli(), fall.endMillis);
    }

    @Test public void dayProgressAndMinuteClampAtBothEdges() {
        TimeAxis.DayWindow day = TimeAxis.day(Instant.parse("2024-02-29T12:34:56Z"), ZoneId.of("UTC"));
        assertEquals(0, day.progress(Long.MIN_VALUE));
        assertEquals(day.minutes, day.progress(Long.MAX_VALUE));
        assertEquals(day.startMillis, day.atMinute(Integer.MIN_VALUE));
        assertEquals(day.endMillis, day.atMinute(Integer.MAX_VALUE));
        assertEquals(754, day.progress(Instant.parse("2024-02-29T12:34:56Z").toEpochMilli()));
    }

    @Test public void invalidAdvanceInputsAreRejected() {
        assertInvalid(() -> TimeAxis.advance(0, -1, 60, 1));
        assertInvalid(() -> TimeAxis.advance(0, 1, 59, 1));
        assertInvalid(() -> TimeAxis.advance(0, 1, 60, 0));
    }

    private static void assertInvalid(Runnable operation) {
        try { operation.run(); fail("Expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }
}
