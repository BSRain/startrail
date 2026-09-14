package com.startrail.app.astro;

import static org.junit.Assert.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;

import org.junit.Test;

public final class WeatherPlannerTest {
    private static final Instant NIGHT = Instant.parse("2026-09-11T12:00:00Z");

    @Test public void clearNightReturnsDistinctChronologicalCandidates() {
        WeatherPlanner.Result result = WeatherPlanner.plan(NIGHT, Instant.parse("2026-09-11T16:00:00Z"),
                22.3, 114.2, clearHours(12, 16), 20);
        assertTrue(result.hasDarkWindow);
        assertFalse(result.picks.isEmpty());
        assertTrue(result.picks.size() <= 5);
        for (int i = 0; i < result.picks.size(); i++) {
            WeatherPlanner.Pick pick = result.picks.get(i);
            assertNotNull(pick.reason);
            assertNotNull(pick.equipment);
            assertTrue(pick.altitudeDegrees >= 20.0);
            assertTrue(pick.weather.hasObservingData());
            if (i > 0) {
                assertFalse(result.picks.get(i - 1).bestTime.isAfter(pick.bestTime));
                assertNotEquals(result.picks.get(i - 1).targetId, pick.targetId);
            }
        }
        try { result.picks.clear(); fail("immutable"); }
        catch (UnsupportedOperationException expected) { }
    }

    @Test public void daylightHasNoCandidatesEvenWithClearWeather() {
        Instant start = Instant.parse("2026-03-20T11:00:00Z");
        WeatherPlanner.Result result = WeatherPlanner.plan(start,
                Instant.parse("2026-03-20T13:00:00Z"), 0, 0,
                Arrays.asList(clear(start), clear(start.plusSeconds(3600)), clear(start.plusSeconds(7200))));
        assertFalse(result.hasDarkWindow);
        assertTrue(result.picks.isEmpty());
    }

    @Test public void severeCloudOrRainRejectsEveryKnownSample() {
        List<WeatherPlanner.Hour> hours = new ArrayList<>();
        for (int h = 12; h <= 16; h++) {
            Instant t = Instant.parse(String.format("2026-09-11T%02d:00:00Z", h));
            hours.add(new WeatherPlanner.Hour(t, h % 2 == 0 ? 80 : 20,
                    h % 2 == 0 ? 0 : 0.11, 60, 8));
        }
        WeatherPlanner.Result result = WeatherPlanner.plan(NIGHT,
                Instant.parse("2026-09-11T16:00:00Z"), 22.3, 114.2, hours);
        assertEquals(0, result.acceptableWeatherSamples);
        assertTrue(result.picks.isEmpty());
    }

    @Test public void missingHourCreatesUnknownSamplesAndNeverAQualifiedPick() {
        WeatherPlanner.Hour only = clear(Instant.parse("2026-09-11T13:00:00Z"));
        WeatherPlanner.Result result = WeatherPlanner.plan(NIGHT,
                Instant.parse("2026-09-11T14:00:00Z"), 22.3, 114.2,
                Collections.singletonList(only), 20);
        assertEquals(7, result.totalSamples);
        assertEquals(3, result.knownWeatherSamples);
        for (WeatherPlanner.Pick pick : result.picks)
            assertEquals(only.time, pick.bestTime.truncatedTo(java.time.temporal.ChronoUnit.HOURS));
        assertTrue(result.message.contains("未知"));
    }

    @Test public void nanEssentialWeatherDegradesToUnknown() {
        WeatherPlanner.Hour missing = new WeatherPlanner.Hour(NIGHT,
                Double.NaN, 0, Double.NaN, Double.NaN);
        WeatherPlanner.Result result = WeatherPlanner.plan(NIGHT,
                Instant.parse("2026-09-11T12:40:00Z"), 22.3, 114.2,
                Collections.singletonList(missing), 20);
        assertEquals(0, result.knownWeatherSamples);
        assertFalse(result.picks.isEmpty());
        assertTrue(result.picks.get(0).reason.contains("天气未知"));
        assertTrue(result.message.contains("缺失"));
    }

    @Test public void daylightCoverageDoesNotSuppressUnknownNightFallback() {
        Instant start = Instant.parse("2026-09-11T08:00:00Z");
        WeatherPlanner.Result result = WeatherPlanner.plan(start,
                Instant.parse("2026-09-11T14:00:00Z"), 22.3, 114.2,
                Collections.singletonList(clear(start)), 20);
        assertTrue(result.hasDarkWindow);
        assertEquals(0, result.knownWeatherSamples);
        assertFalse(result.picks.isEmpty());
        assertTrue(result.picks.get(0).reason.contains("天气未知"));
    }

    @Test public void knownBadMeasurementVetoesPartiallyMissingHour() {
        WeatherPlanner.Hour rain = new WeatherPlanner.Hour(NIGHT,
                Double.NaN, 10, Double.NaN, Double.NaN);
        WeatherPlanner.Result wet = WeatherPlanner.plan(NIGHT, NIGHT.plusSeconds(40 * 60),
                22.3, 114.2, Collections.singletonList(rain), 20);
        assertTrue(wet.picks.isEmpty());

        WeatherPlanner.Hour gale = new WeatherPlanner.Hour(NIGHT, 10, 0, 60, 40);
        WeatherPlanner.Result windy = WeatherPlanner.plan(NIGHT, NIGHT.plusSeconds(40 * 60),
                22.3, 114.2, Collections.singletonList(gale), 20);
        assertTrue(windy.picks.isEmpty());
        assertEquals(0, windy.acceptableWeatherSamples);
    }

    @Test public void validatesWindowWeatherAndSampling() {
        assertInvalid(() -> WeatherPlanner.plan(NIGHT, NIGHT, 0, 0, Collections.emptyList()));
        assertInvalid(() -> WeatherPlanner.plan(NIGHT, NIGHT.plusSeconds(25 * 3600), 0, 0,
                Collections.emptyList()));
        assertInvalid(() -> WeatherPlanner.plan(NIGHT, NIGHT.plusSeconds(3600), 91, 0,
                Collections.emptyList()));
        assertInvalid(() -> WeatherPlanner.plan(NIGHT, NIGHT.plusSeconds(3600), 0, 181,
                Collections.emptyList()));
        assertInvalid(() -> WeatherPlanner.plan(NIGHT, NIGHT.plusSeconds(3600), 0, 0,
                Collections.emptyList(), 9));
        assertInvalid(() -> new WeatherPlanner.Hour(NIGHT.plusSeconds(1), 20, 0, 50, 5));
        assertInvalid(() -> new WeatherPlanner.Hour(NIGHT, 101, 0, 50, 5));
        WeatherPlanner.Hour duplicate = clear(NIGHT);
        assertInvalid(() -> WeatherPlanner.plan(NIGHT, NIGHT.plusSeconds(3600), 0, 0,
                Arrays.asList(duplicate, duplicate)));
        Instant minimum = Instant.ofEpochMilli(TimeAxis.MIN_MILLIS);
        Instant maximum = Instant.ofEpochMilli(TimeAxis.MAX_MILLIS);
        assertInvalid(() -> WeatherPlanner.plan(minimum.minusMillis(1), minimum.plusSeconds(1),
                0, 0, Collections.emptyList()));
        assertInvalid(() -> WeatherPlanner.plan(maximum.minusSeconds(1), maximum.plusMillis(1),
                0, 0, Collections.emptyList()));
    }

    @Test public void honorsThreadInterruption() {
        Thread.currentThread().interrupt();
        try {
            WeatherPlanner.plan(NIGHT, NIGHT.plusSeconds(3600), 22.3, 114.2,
                    Collections.singletonList(clear(NIGHT)));
            fail("expected cancellation");
        } catch (CancellationException expected) {
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private static List<WeatherPlanner.Hour> clearHours(int first, int last) {
        List<WeatherPlanner.Hour> result = new ArrayList<>();
        for (int h = first; h <= last; h++)
            result.add(clear(Instant.parse(String.format("2026-09-11T%02d:00:00Z", h))));
        return result;
    }

    private static WeatherPlanner.Hour clear(Instant time) {
        return new WeatherPlanner.Hour(time, 10, 0, 55, 8);
    }

    private static void assertInvalid(Runnable action) {
        try { action.run(); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }
}
