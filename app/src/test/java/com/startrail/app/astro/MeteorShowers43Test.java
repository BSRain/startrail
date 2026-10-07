package com.startrail.app.astro;

import static org.junit.Assert.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;

import org.junit.Test;

public final class MeteorShowers43Test {
    @Test public void returnsMajor2026ShowersAtExpectedCalendarDates() {
        List<CelestialEvents.Event> events = MeteorShowers43.search(
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"));

        assertEquals(8, events.size());
        assertPeakDay(events, "QUA", "2026-01-03T00:00:00Z", "2026-01-05T00:00:00Z");
        assertPeakDay(events, "LYR", "2026-04-21T00:00:00Z", "2026-04-24T00:00:00Z");
        assertPeakDay(events, "ETA", "2026-05-05T00:00:00Z", "2026-05-07T00:00:00Z");
        assertPeakDay(events, "SDA", "2026-07-30T00:00:00Z", "2026-08-01T00:00:00Z");
        assertPeakDay(events, "PER", "2026-08-12T00:00:00Z", "2026-08-14T00:00:00Z");
        assertPeakDay(events, "ORI", "2026-10-20T00:00:00Z", "2026-10-23T00:00:00Z");
        assertPeakDay(events, "LEO", "2026-11-16T00:00:00Z", "2026-11-19T00:00:00Z");
        assertPeakDay(events, "GEM", "2026-12-13T00:00:00Z", "2026-12-16T00:00:00Z");

        // These minute-level checks distinguish the IMO J2000 longitude convention
        // from Astronomy Engine's equinox-of-date search (about nine hours later).
        assertPeakNear(events, "QUA", "2026-01-03T21:24:03Z");
        assertPeakNear(events, "PER", "2026-08-13T03:24:25Z");
        assertPeakNear(events, "GEM", "2026-12-14T13:49:22Z");
    }

    @Test public void supportsThe2027CalendarAndCarriesPlanningMetadata() {
        List<CelestialEvents.Event> events = MeteorShowers43.search(
                Instant.parse("2026-12-20T00:00:00Z"),
                Instant.parse("2027-12-20T00:00:00Z"));

        assertEquals(8, events.size());
        Set<String> ids = new HashSet<>();
        Instant previous = Instant.MIN;
        for (CelestialEvents.Event event : events) {
            assertEquals(CelestialEvents.Kind.METEOR_SHOWER, event.kind);
            assertTrue(event.title.endsWith("流星雨（预计峰期）"));
            assertTrue(event.description.contains("通常峰值"));
            assertTrue(event.description.contains("不保证"));
            assertTrue(event.approximate);
            assertTrue(event.local);
            assertEquals("", event.primaryId);
            assertEquals("", event.secondaryId);
            assertEquals(-1, event.phase);
            assertTrue(Double.isNaN(event.separationDegrees));
            assertTrue(event.radiantRaHours >= 0 && event.radiantRaHours < 24);
            assertTrue(event.radiantDecDegrees >= -90 && event.radiantDecDegrees <= 90);
            assertTrue(event.contacts.isEmpty());
            assertEquals(event.peak.minusSeconds(36 * 3600), event.start);
            assertEquals(event.peak.plusSeconds(36 * 3600), event.end);
            assertFalse(event.peak.isBefore(previous));
            assertTrue(ids.add(event.id));
            previous = event.peak;
        }
        assertTrue(events.get(0).peak.isAfter(Instant.parse("2027-01-03T00:00:00Z")));
        assertPeakNear(events, "QUA", "2027-01-04T03:34:13Z");
        assertPeakNear(events, "PER", "2027-08-13T09:34:16Z");
        assertPeakNear(events, "GEM", "2027-12-14T20:02:19Z");
        assertTrue(events.get(0).sourceUrl.contains("ShCal27s.pdf"));
        try { events.clear(); fail("expected immutable result"); }
        catch (UnsupportedOperationException expected) { }
    }

    @Test public void filtersHalfOpenRangesAndReturnsNoUnverifiedYears() {
        List<CelestialEvents.Event> january = MeteorShowers43.search(
                Instant.parse("2027-01-01T00:00:00Z"),
                Instant.parse("2027-02-01T00:00:00Z"));
        assertEquals(1, january.size());
        assertTrue(january.get(0).id.contains("QUA"));

        assertTrue(MeteorShowers43.search(
                Instant.parse("2028-01-01T00:00:00Z"),
                Instant.parse("2028-02-01T00:00:00Z")).isEmpty());
    }

    @Test public void validatesBoundedRangeAndHonorsCancellation() {
        assertInvalid(() -> MeteorShowers43.search(null, Instant.now()));
        assertInvalid(() -> MeteorShowers43.search(
                Instant.parse("2027-01-02T00:00:00Z"), Instant.parse("2027-01-01T00:00:00Z")));
        assertInvalid(() -> MeteorShowers43.search(
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2027-01-03T00:00:00Z")));

        Thread.currentThread().interrupt();
        try {
            MeteorShowers43.search(Instant.parse("2027-01-01T00:00:00Z"),
                    Instant.parse("2027-01-02T00:00:00Z"));
            fail("expected cancellation");
        } catch (CancellationException expected) {
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }

    private static void assertPeakDay(List<CelestialEvents.Event> events, String code,
                                      String earliest, String latest) {
        for (CelestialEvents.Event event : events) {
            if (event.id.contains("-" + code + "-")) {
                assertFalse(event.peak.isBefore(Instant.parse(earliest)));
                assertTrue(event.peak.isBefore(Instant.parse(latest)));
                return;
            }
        }
        fail("missing shower " + code);
    }

    private static void assertPeakNear(List<CelestialEvents.Event> events, String code,
                                       String expected) {
        Instant target = Instant.parse(expected);
        for (CelestialEvents.Event event : events) {
            if (event.id.contains("-" + code + "-")) {
                long errorMillis = Math.abs(event.peak.toEpochMilli() - target.toEpochMilli());
                assertTrue(code + " peak differs by " + errorMillis + " ms", errorMillis < 60_000);
                return;
            }
        }
        fail("missing shower " + code);
    }

    private static void assertInvalid(Runnable action) {
        try { action.run(); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }
}
