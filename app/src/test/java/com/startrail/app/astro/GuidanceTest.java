package com.startrail.app.astro;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class GuidanceTest {
    private static final double EPS = 1e-6;

    @Test public void alignmentThresholdIsInclusive() {
        SkyCamera.Basis camera = SkyCamera.manualBasis(0, 0, 0);
        Guidance.Result edge = Guidance.calculate(SkyCamera.horizontalVector(0, 2), camera, 2);
        assertEquals(2, edge.angularSeparation, EPS);
        assertTrue(edge.aligned);
        assertFalse(Guidance.calculate(SkyCamera.horizontalVector(0, 2.001), camera, 2).aligned);
    }

    @Test public void belowHorizonCannotAlignEvenWhenCameraPointsAtTarget() {
        SkyCamera.Basis camera = SkyCamera.manualBasis(0, -20, 0);
        Guidance.Result result = Guidance.calculate(SkyCamera.horizontalVector(-20, 0), camera, 2);
        assertEquals(0, result.angularSeparation, EPS);
        assertTrue(result.belowHorizon);
        assertFalse(result.aligned);
    }

    @Test public void antipodeGetsDeterministicFiniteArrow() {
        Guidance.Result result = Guidance.calculate(new double[]{0, -1, 0}, SkyCamera.manualBasis(0, 0, 0), 0);
        assertEquals(180, result.angularSeparation, EPS);
        assertEquals(0, result.screenX, EPS);
        assertEquals(-1, result.screenY, EPS);
    }

    @Test public void cueDeadZoneAndManualDirectionAreBoundaryChecked() {
        assertEquals("向中央转动手机", Guidance.directionCue(0.249999, -0.249999, false));
        assertEquals("向右上转动手机", Guidance.directionCue(0.25, -0.25, false));
        assertEquals("向左下拖动星图", Guidance.directionCue(0.25, -0.25, true));
    }

    @Test public void invalidInputsAreRejected() {
        SkyCamera.Basis camera = SkyCamera.manualBasis(0, 0, 0);
        assertInvalid(() -> Guidance.calculate(null, camera, 1));
        assertInvalid(() -> Guidance.calculate(new double[]{0, 0, 0}, camera, 1));
        assertInvalid(() -> Guidance.calculate(new double[]{0, 1, 0}, null, 1));
        assertInvalid(() -> Guidance.calculate(new double[]{0, 1, 0}, camera, -Math.ulp(0.0)));
        assertInvalid(() -> Guidance.directionCue(Double.NaN, 0, false));
    }

    private static void assertInvalid(Runnable operation) {
        try { operation.run(); fail("Expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }
}
