package com.startrail.app.astro;

import static org.junit.Assert.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CancellationException;

import org.junit.Test;

public final class CelestialEventsTest {
    @Test public void findsKnownTotalLunarEclipseWithChronologicalContacts() {
        List<CelestialEvents.Event> events = CelestialEvents.search(
                Instant.parse("2025-09-06T00:00:00Z"),
                Instant.parse("2025-09-09T00:00:00Z"), 22.3, 114.2);
        CelestialEvents.Event eclipse = find(events, CelestialEvents.Kind.LUNAR_ECLIPSE);
        assertTrue(eclipse.title.contains("红月"));
        assertNear("2025-09-07T18:12:00Z", eclipse.peak, Duration.ofMinutes(5));
        assertTrue(eclipse.contacts.size() >= 7);
        assertChronological(eclipse.contacts);
        assertEquals(eclipse.contacts.get(0).time, eclipse.start);
        assertEquals(eclipse.contacts.get(eclipse.contacts.size() - 1).time, eclipse.end);
    }

    @Test public void findsDallasTotalSolarEclipseAndActualContacts() {
        List<CelestialEvents.Event> events = CelestialEvents.search(
                Instant.parse("2024-04-08T00:00:00Z"),
                Instant.parse("2024-04-09T00:00:00Z"), 32.7767, -96.7970);
        CelestialEvents.Event eclipse = find(events, CelestialEvents.Kind.SOLAR_ECLIPSE);
        assertTrue(eclipse.local);
        assertTrue(eclipse.title.contains("日全食"));
        assertNear("2024-04-08T18:42:00Z", eclipse.peak, Duration.ofMinutes(5));
        assertEquals("sun", eclipse.primaryId);
        assertEquals("moon", eclipse.secondaryId);
        assertTrue(eclipse.contacts.size() >= 5);
        assertChronological(eclipse.contacts);
    }

    @Test public void solarRangeIsBasedOnLocalPeakInsteadOfGlobalPeak(){
        Instant start=Instant.parse("2024-04-08T18:30:00Z"),end=Instant.parse("2024-04-08T19:00:00Z");
        CelestialEvents.Event eclipse=find(CelestialEvents.search(start,end,32.7767,-96.7970),CelestialEvents.Kind.SOLAR_ECLIPSE);
        assertFalse(eclipse.peak.isBefore(start));assertTrue(eclipse.peak.isBefore(end));
    }
    @Test public void remoteGlobalSolarEclipseIsMarkedNonLocal() {
        CelestialEvents.Event eclipse = find(CelestialEvents.search(
                Instant.parse("2024-04-08T00:00:00Z"),
                Instant.parse("2024-04-09T00:00:00Z"), -33.8688, 151.2093),
                CelestialEvents.Kind.SOLAR_ECLIPSE);
        assertFalse(eclipse.local);
        assertTrue(eclipse.contacts.isEmpty());
    }

    @Test public void returnsAllFourQuarterCodesAndImmutableSortedResults() {
        List<CelestialEvents.Event> events = CelestialEvents.search(
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-02-15T00:00:00Z"), 0.0, 0.0);
        boolean[] phases = new boolean[4];
        Instant previous = Instant.MIN;
        for (CelestialEvents.Event event : events) {
            assertFalse(event.peak.isBefore(previous));
            previous = event.peak;
            if (event.kind == CelestialEvents.Kind.MOON_PHASE) phases[event.phase] = true;
        }
        for (boolean present : phases) assertTrue(present);
        try { events.clear(); fail("expected immutable result"); }
        catch (UnsupportedOperationException expected) { }
    }

    @Test public void closeApproachesAreTrueThresholdedLocalMinima() {
        List<CelestialEvents.Event> events = CelestialEvents.search(
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-04-01T00:00:00Z"), 0.0, 0.0);
        int count = 0;
        for (CelestialEvents.Event event : events) if (event.kind == CelestialEvents.Kind.CLOSE_APPROACH) {
            count++;
            assertTrue(event.approximate);
            double limit = "moon".equals(event.primaryId) ? 8.0 : 5.0;
            assertTrue(event.separationDegrees <= limit);
            assertTrue(event.title.contains("视角最近"));
            assertFalse(event.description.contains("赤经合"));
        }
        assertTrue("expected at least one genuine close approach", count > 0);
    }

    @Test public void validatesRangeLocationAndInterruption() {
        assertInvalid(() -> CelestialEvents.search(null, Instant.now(), 0, 0));
        assertInvalid(() -> CelestialEvents.search(Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-03T00:00:00Z"), 0, 0));
        assertInvalid(() -> CelestialEvents.search(Instant.parse("1899-01-01T00:00:00Z"),
                Instant.parse("1899-01-02T00:00:00Z"), 0, 0));
        assertInvalid(() -> CelestialEvents.search(Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"), 91, 0));
        Thread.currentThread().interrupt();
        try {
            CelestialEvents.search(Instant.parse("2026-01-01T00:00:00Z"),
                    Instant.parse("2026-01-02T00:00:00Z"), 0, 0);
            fail("expected cancellation");
        } catch (CancellationException expected) {
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }

    private static CelestialEvents.Event find(List<CelestialEvents.Event> events,
                                               CelestialEvents.Kind kind) {
        for (CelestialEvents.Event event : events) if (event.kind == kind) return event;
        throw new AssertionError("missing " + kind);
    }

    private static void assertChronological(List<CelestialEvents.Contact> contacts) {
        for (int i = 1; i < contacts.size(); i++)
            assertFalse(contacts.get(i).time.isBefore(contacts.get(i - 1).time));
    }

    private static void assertNear(String expected, Instant actual, Duration tolerance) {
        assertTrue(Math.abs(Duration.between(Instant.parse(expected), actual).toMillis())
                <= tolerance.toMillis());
    }

    private static void assertInvalid(Runnable action) {
        try { action.run(); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }
}
