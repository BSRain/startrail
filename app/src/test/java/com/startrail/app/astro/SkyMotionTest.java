package com.startrail.app.astro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.time.Instant;
import org.junit.Test;

public final class SkyMotionTest {
    private static final double WIDTH = 1200.0;
    private static final double HEIGHT = 800.0;
    private static final double FOV = 70.0;

    @Test public void cachedStarAndRotatedCameraMatchFreshAstronomyAtBothTimeDirections() {
        Instant time = Instant.parse("2026-09-11T12:34:56Z");
        double[] latitudes = {0.0, 35.0, -33.9, 89.5, -89.5};
        double[] offsets = {-45.0, 45.0, -600.0, 600.0};
        for (double latitude : latitudes) {
            for (double offset : offsets) {
                assertProjectionEquivalent(time, latitude, 114.2, offset);
            }
        }
    }

    @Test public void positiveOffsetRotatesCameraPositivelyAboutNorthCelestialPole() {
        SkyCamera.Basis camera = new SkyCamera.Basis(
                new double[]{1, 0, 0}, new double[]{0, 0, 1}, new double[]{0, 1, 0});
        double quarterTurnSeconds = 86400.0 * 90.0 / SkyMotion.SIDEREAL_DEGREES_PER_DAY;
        SkyCamera.Basis moved = SkyMotion.forSky(camera, 0.0, quarterTurnSeconds);
        assertVector(new double[]{0, 0, -1}, moved.right, 1e-12);
        assertVector(new double[]{1, 0, 0}, moved.up, 1e-12);
        assertVector(new double[]{0, 1, 0}, moved.forward, 1e-12);
    }

    @Test public void rejectsInvalidInputs() {
        SkyCamera.Basis camera = SkyCamera.manualBasis(0, 0, 0);
        assertInvalid(() -> SkyMotion.forSky(null, 0, 0));
        assertInvalid(() -> SkyMotion.forSky(camera, 90.1, 0));
        assertInvalid(() -> SkyMotion.forSky(camera, -90.1, 0));
        assertInvalid(() -> SkyMotion.forSky(camera, Double.NaN, 0));
        assertInvalid(() -> SkyMotion.forSky(camera, 0, Double.POSITIVE_INFINITY));
    }

    private static void assertProjectionEquivalent(Instant time, double latitude,
                                                    double longitude, double offsetSeconds) {
        Astronomy.HorizontalPosition oldPosition = Astronomy.horizontal(
                6.752481, -16.716116, time, latitude, longitude);
        Astronomy.HorizontalPosition newPosition = Astronomy.horizontal(
                6.752481, -16.716116, time.plusMillis(Math.round(offsetSeconds * 1000.0)),
                latitude, longitude);
        double[] oldVector = vector(oldPosition);
        double[] newVector = vector(newPosition);
        SkyCamera.Basis camera = cameraLookingAt(newVector);

        SkyCamera.Point interpolated = SkyCamera.project(oldVector,
                SkyMotion.forSky(camera, latitude, offsetSeconds), FOV, WIDTH, HEIGHT);
        SkyCamera.Point fresh = SkyCamera.project(newVector, camera, FOV, WIDTH, HEIGHT);

        assertEquals(fresh.x, interpolated.x, 2e-4);
        assertEquals(fresh.y, interpolated.y, 2e-4);
        assertEquals(fresh.depth, interpolated.depth, 2e-7);
    }

    private static SkyCamera.Basis cameraLookingAt(double[] forward) {
        double[] reference = Math.abs(forward[2]) < 0.9
                ? new double[]{0, 0, 1} : new double[]{0, 1, 0};
        double[] right = cross(forward, reference);
        return new SkyCamera.Basis(right, reference, forward);
    }

    private static double[] vector(Astronomy.HorizontalPosition position) {
        return SkyCamera.horizontalVector(position.altitudeDegrees, position.azimuthDegrees);
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]};
    }

    private static void assertVector(double[] expected, double[] actual, double tolerance) {
        for (int i = 0; i < 3; i++) assertEquals(expected[i], actual[i], tolerance);
    }

    private static void assertInvalid(Runnable operation) {
        try { operation.run(); fail("Expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }
}
