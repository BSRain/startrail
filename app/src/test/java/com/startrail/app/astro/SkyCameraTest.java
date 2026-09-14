package com.startrail.app.astro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class SkyCameraTest {
    private static final double EPS = 1e-9;

    @Test public void cardinalDirectionsUseEastNorthUpAxes() {
        assertVector(new double[]{0, 1, 0}, SkyCamera.horizontalVector(0, 0));
        assertVector(new double[]{1, 0, 0}, SkyCamera.horizontalVector(0, 90));
        assertVector(new double[]{0, 0, 1}, SkyCamera.horizontalVector(90, 200));
    }

    @Test public void basisIsOrthonormalEvenWhenInputRightHasForwardComponent() {
        SkyCamera.Basis basis = new SkyCamera.Basis(
                new double[]{1, 1, 0}, new double[]{0, 0, 99}, new double[]{0, 2, 0});
        assertEquals(1, length(basis.right), EPS);
        assertEquals(1, length(basis.up), EPS);
        assertEquals(1, length(basis.forward), EPS);
        assertEquals(0, dot(basis.right, basis.forward), EPS);
        assertEquals(0, dot(basis.up, basis.forward), EPS);
    }

    @Test public void projectionAndRayAreInverseAtViewportEdges() {
        SkyCamera.Basis camera = SkyCamera.manualBasis(35, 20, 17);
        for (double[] pixel : new double[][]{{0, 0}, {320, 0}, {0, 180}, {320, 180}, {160, 90}}) {
            double[] ray = SkyCamera.rayForPixel(pixel[0], pixel[1], camera, 60, 320, 180);
            SkyCamera.Point projected = SkyCamera.project(ray, camera, 60, 320, 180);
            assertTrue(projected.inFront);
            assertEquals(pixel[0], projected.x, 1e-7);
            assertEquals(pixel[1], projected.y, 1e-7);
        }
    }

    @Test public void behindAndPerpendicularDirectionsAreClipped() {
        SkyCamera.Basis camera = SkyCamera.manualBasis(0, 0, 0);
        SkyCamera.Point behind = SkyCamera.project(new double[]{0, -1, 0}, camera, 60, 100, 100);
        assertFalse(behind.visible);
        assertFalse(behind.inFront);
        assertTrue(Double.isNaN(behind.x));
        SkyCamera.Point perpendicular = SkyCamera.project(new double[]{1, 0, 0}, camera, 60, 100, 100);
        assertFalse(perpendicular.inFront);
    }

    @Test public void invalidViewportAndDegenerateBasisAreRejected() {
        SkyCamera.Basis camera = SkyCamera.manualBasis(0, 0, 0);
        assertInvalid(() -> SkyCamera.project(camera.forward, camera, 0, 100, 100));
        assertInvalid(() -> SkyCamera.project(camera.forward, camera, 180, 100, 100));
        assertInvalid(() -> SkyCamera.rayForPixel(0, 0, camera, 60, 0, 100));
        assertInvalid(() -> new SkyCamera.Basis(new double[]{0, 1, 0}, new double[]{0, 0, 1}, new double[]{0, 0, 0}));
        assertInvalid(() -> new SkyCamera.Basis(new double[]{0, 2, 0}, new double[]{0, 0, 1}, new double[]{0, 1, 0}));
    }

    private static double dot(double[] a, double[] b) { return a[0]*b[0]+a[1]*b[1]+a[2]*b[2]; }
    private static double length(double[] v) { return Math.sqrt(dot(v, v)); }
    private static void assertVector(double[] expected, double[] actual) {
        for (int i=0;i<3;i++) assertEquals(expected[i], actual[i], EPS);
    }
    private static void assertInvalid(Runnable operation) {
        try { operation.run(); fail("Expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }
}
