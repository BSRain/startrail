package com.startrail.app.astro;

import static org.junit.Assert.*;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CancellationException;

import org.junit.Test;

public final class ObservingEventsTest {
    private static final Instant START = Instant.parse("2026-09-11T00:00:00Z");

    @Test public void equatorialTargetRisesSetsAndTransits() {
        Star target = star("equator", 6.0, 0.0);
        ObservingEvents.Result result = ObservingEvents.calculate(target, START, 0.0, 0.0);
        assertNotNull(result.rise); assertNotNull(result.set); assertNotNull(result.transit);
        assertEquals(90.0, result.transitAltitude, 0.02);
        assertEquals(145, result.samples.size());
        assertEquals(START, result.start);
        assertEquals(START.plus(Duration.ofHours(24)), result.end);
        assertEquals(result.start, result.samples.get(0).time);
        assertEquals(result.end, result.samples.get(144).time);
        assertTrue(Math.abs(altitude(target, result.rise, 0.0, 0.0)) < 0.06);
        assertTrue(Math.abs(altitude(target, result.set, 0.0, 0.0)) < 0.06);
    }

    @Test public void northernAndSouthernLatitudesHaveExpectedCulminations() {
        Star north = star("north", 10.0, 30.0);
        ObservingEvents.Result atNorth = ObservingEvents.calculate(north, Instant.parse("2000-01-01T12:00:00Z"), 45.0, 20.0);
        ObservingEvents.Result atSouth = ObservingEvents.calculate(north, Instant.parse("2000-01-01T12:00:00Z"), -45.0, 20.0);
        assertEquals(75.0, atNorth.transitAltitude, 0.03);
        assertEquals(15.0, atSouth.transitAltitude, 0.03);
    }

    @Test public void polarTargetsExposeNullHorizonEvents() {
        ObservingEvents.Result alwaysUp = ObservingEvents.calculate(
                star("always_up", 2.0, 80.0), START, 70.0, 0.0);
        assertNull(alwaysUp.rise); assertNull(alwaysUp.set);
        for (ObservingEvents.Sample sample : alwaysUp.samples) assertTrue(sample.altitude > 0.0);

        ObservingEvents.Result alwaysDown = ObservingEvents.calculate(
                star("always_down", 2.0, -80.0), START, 70.0, 0.0);
        assertNull(alwaysDown.rise); assertNull(alwaysDown.set);
        for (ObservingEvents.Sample sample : alwaysDown.samples) assertTrue(sample.altitude < 0.0);
    }

    @Test public void sunAndMoonUseDynamicEphemerides() {
        for (String id : new String[] {"sun", "moon"}) {
            ObservingEvents.Result result = ObservingEvents.calculate(star(id, 0.0, 0.0),
                    START, 22.3, 114.2);
            assertNotNull(result.rise); assertNotNull(result.set); assertNotNull(result.transit);
            assertTrue(Double.isFinite(result.transitAltitude));
            assertEquals(145, result.samples.size());
            assertTrue(Math.abs(bodyAltitude(id, result.rise, 22.3, 114.2)) < 0.08);
        }
    }

    @Test public void instantsDoNotDependOnTimeZoneOrDateLineConvention() {
        Star target = star("zone_free", 12.0, -10.0);
        ObservingEvents.Result east = ObservingEvents.calculate(target, START, -33.9, 179.0);
        ObservingEvents.Result west = ObservingEvents.calculate(target, START, -33.9, -179.0);
        assertEquals(START.plus(Duration.ofHours(24)), east.end);
        assertEquals(START.plus(Duration.ofHours(24)), west.end);
        long separation = Math.abs(Duration.between(east.transit, west.transit).getSeconds());
        assertTrue(separation < 9 * 60);
    }

    @Test public void validatesArgumentsAndHonorsInterruption() {
        assertInvalid(() -> ObservingEvents.calculate(null, START, 0.0, 0.0));
        assertInvalid(() -> ObservingEvents.calculate(star("x", 0.0, 0.0), null, 0.0, 0.0));
        assertInvalid(() -> ObservingEvents.calculate(star("x", 0.0, 0.0), START, 91.0, 0.0));
        assertInvalid(() -> ObservingEvents.calculate(star("x", 0.0, 0.0), START, 0.0, 181.0));
        Thread.currentThread().interrupt();
        try {
            ObservingEvents.calculate(star("x", 0.0, 0.0), START, 0.0, 0.0);
            fail("expected cancellation");
        } catch (CancellationException expected) {
            // CancellationException intentionally preserves the interrupted flag.
        } finally { Thread.interrupted(); }
    }

    @Test public void clipsTheWindowAtTheSupportedTimeAxisEnd() {
        Instant start = Instant.ofEpochMilli(TimeAxis.MAX_MILLIS).minus(Duration.ofMinutes(5));
        ObservingEvents.Result result = ObservingEvents.calculate(star("edge", 0.0, 0.0),
                start, 0.0, 0.0);
        assertEquals(Instant.ofEpochMilli(TimeAxis.MAX_MILLIS), result.end);
        assertEquals(2, result.samples.size());
        assertEquals(result.end, result.samples.get(1).time);
    }

    @Test public void solarTransitsMatchEngineHourAngleSearch() {
        io.github.cosinekitty.astronomy.Observer observer=new io.github.cosinekitty.astronomy.Observer(22.3,114.2,0);
        double ut=START.getEpochSecond()/86400.0-10957.5;
        io.github.cosinekitty.astronomy.Time engineStart=new io.github.cosinekitty.astronomy.Time(ut);
        for(String id:new String[]{"sun","moon"}){
            io.github.cosinekitty.astronomy.Body body=id.equals("sun")?io.github.cosinekitty.astronomy.Body.Sun:io.github.cosinekitty.astronomy.Body.Moon;
            io.github.cosinekitty.astronomy.HourAngleInfo expected=io.github.cosinekitty.astronomy.Astronomy.searchHourAngle(body,observer,0,engineStart,1);
            ObservingEvents.Result actual=ObservingEvents.calculate(star(id,0,0),START,22.3,114.2);
            assertNotNull(actual.transit);
            double foundUt=actual.transit.getEpochSecond()/86400.0+actual.transit.getNano()/86400e9-10957.5;
            assertEquals(expected.getTime().getUt(),foundUt,30.0/86400.0);
        }
    }

    private static Star star(String id, double ra, double dec) {
        return new Star(id, id, id, "test", ra, dec, 0.0);
    }
    private static double altitude(Star star, Instant time, double lat, double lon) {
        return Astronomy.horizontal(star, time, lat, lon).altitudeDegrees;
    }
    private static double bodyAltitude(String id, Instant time, double lat, double lon) {
        for (SolarSystem.BodyPosition body : SolarSystem.snapshot(time, lat, lon))
            if (id.equals(body.id)) return body.altitude;
        throw new AssertionError(id);
    }
    private static void assertInvalid(Runnable action) {
        try { action.run(); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }
}
