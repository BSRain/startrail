package com.startrail.app.astro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import org.junit.Test;

public final class AstronomyTest {
    private static final double EPS = 1e-9;

    @Test public void julianDateUsesEpochAndNanosecondFraction() {
        assertEquals(2440587.5, Astronomy.julianDate(Instant.EPOCH), 1e-10);
        assertEquals(2451545.0, Astronomy.julianDate(Instant.parse("2000-01-01T12:00:00Z")), 1e-10);
        assertEquals(1.0 / 86400.0,
                Astronomy.julianDate(Instant.EPOCH.plusSeconds(1)) - Astronomy.julianDate(Instant.EPOCH),
                1e-9);
    }

    @Test public void degreeNormalizationWrapsBothDirections() {
        assertEquals(0, Astronomy.normalizeDegrees(720), EPS);
        assertEquals(359.5, Astronomy.normalizeDegrees(-0.5), EPS);
        assertEquals(0, Astronomy.normalizeDegrees(-360), EPS);
    }

    @Test public void j2000PrecessionIsIdentityIncludingNearPoles() {
        double[] ordinary = Astronomy.precessJ2000(23.999, -45, 0);
        assertEquals(23.999, ordinary[0], EPS);
        assertEquals(-45, ordinary[1], EPS);
        double[] pole = Astronomy.precessJ2000(12, 90, 0);
        assertEquals(90, pole[1], EPS);
        assertTrue(pole[0] >= 0 && pole[0] < 24);
    }

    @Test public void equatorialTransitAndHorizonVisibilityAreStable() {
        Instant j2000 = Instant.parse("2000-01-01T12:00:00Z");
        Astronomy.HorizontalPosition transit = Astronomy.horizontal(18.697374558, 0, j2000, 0, 0);
        assertEquals(90, transit.altitudeDegrees, 0.0001);
        Astronomy.HorizontalPosition horizon = new Astronomy.HorizontalPosition(0, -1);
        assertTrue(horizon.visible);
        assertTrue(horizon.isAboveHorizon());
        assertEquals(359, horizon.azimuthDegrees, EPS);
        assertFalse(new Astronomy.HorizontalPosition(-Math.ulp(0.0), 0).visible);
    }

    @Test public void invalidCoordinatesAreRejectedAtTheirOpenAndClosedBounds() {
        assertInvalid(() -> Astronomy.horizontal(24, 0, Instant.EPOCH, 0, 0));
        assertInvalid(() -> Astronomy.horizontal(-Math.ulp(0.0), 0, Instant.EPOCH, 0, 0));
        assertInvalid(() -> Astronomy.horizontal(0, 90.0001, Instant.EPOCH, 0, 0));
        assertInvalid(() -> Astronomy.horizontal(0, 0, Instant.EPOCH, -90.0001, 0));
        assertInvalid(() -> Astronomy.horizontal(0, 0, Instant.EPOCH, 0, 180.0001));
        assertInvalid(() -> Astronomy.julianDate(null));
    }

    private static void assertInvalid(Runnable operation) {
        try { operation.run(); fail("Expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }
}
