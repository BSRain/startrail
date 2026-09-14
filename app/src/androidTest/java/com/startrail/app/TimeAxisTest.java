package com.startrail.app;

import com.startrail.app.astro.TimeAxis;

import java.time.Instant;
import java.time.ZoneId;

import junit.framework.TestCase;

public final class TimeAxisTest extends TestCase {
    public void testClampRangeAndOverflowSafeAdvance() {
        assertEquals(TimeAxis.MIN_MILLIS, TimeAxis.clamp(Long.MIN_VALUE));
        assertEquals(TimeAxis.MAX_MILLIS, TimeAxis.clamp(Long.MAX_VALUE));
        assertEquals(TimeAxis.MAX_MILLIS,
                TimeAxis.advance(TimeAxis.MAX_MILLIS - 1, Long.MAX_VALUE, 3600, 1));
        assertEquals(TimeAxis.MIN_MILLIS,
                TimeAxis.advance(TimeAxis.MIN_MILLIS + 1, Long.MAX_VALUE, 3600, -1));
    }

    public void testLeapDayAndReverseProgression() {
        long leapDay = Instant.parse("2024-02-28T23:59:00Z").toEpochMilli();
        long advanced = TimeAxis.advance(leapDay, 60_000L, 60, 1);
        assertEquals(Instant.parse("2024-02-29T00:59:00Z").toEpochMilli(), advanced);
        assertEquals(leapDay, TimeAxis.advance(advanced, 60_000L, 60, -1));
    }

    public void testNewYorkSpringAndFallWindows() {
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

    public void testDayProgressRoundTripsToOneMinute() {
        TimeAxis.DayWindow day = TimeAxis.day(
                Instant.parse("2024-02-29T12:34:56Z"), ZoneId.of("UTC"));
        long sample = Instant.parse("2024-02-29T12:34:56Z").toEpochMilli();
        int minute = day.progress(sample);
        assertTrue(Math.abs(sample - day.atMinute(minute)) < 60_000L);
        assertEquals(day.startMillis, day.atMinute(-5));
        assertEquals(day.endMillis, day.atMinute(day.minutes + 5));
    }

    public void testInvalidAdvanceInputs() {
        try {
            TimeAxis.advance(0, -1, 60, 1);
            fail("negative elapsed time accepted");
        } catch (IllegalArgumentException expected) {}
        try {
            TimeAxis.advance(0, 1, 1, 1);
            fail("unsupported rate accepted");
        } catch (IllegalArgumentException expected) {}
        try {
            TimeAxis.advance(0, 1, 60, 0);
            fail("invalid direction accepted");
        } catch (IllegalArgumentException expected) {}
    }
}
