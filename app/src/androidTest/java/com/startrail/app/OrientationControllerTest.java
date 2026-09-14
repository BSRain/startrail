package com.startrail.app;

import junit.framework.TestCase;
import android.view.Surface;
import com.startrail.app.astro.SkyCamera;

/** Synthetic device matrices verify rear-camera and display-rotation conventions. */
public final class OrientationControllerTest extends TestCase {
    private static void near(double expected, double actual) { assertEquals(expected, actual, 1e-6); }

    public void testFlatPhoneLooksDownWithTopTowardNorth() {
        SkyCamera.Frame f = OrientationController.cameraFrame(
                new float[]{1,0,0, 0,1,0, 0,0,1}, Surface.ROTATION_0, 0);
        near(1, f.right[0]); near(1, f.up[1]); near(-1, f.forward[2]);
    }

    public void testVerticalPhoneRearFacesNorth() {
        // Device X=east, device Y=up, device Z=south, so rear (-Z) faces north.
        float[] northVertical = {1,0,0, 0,0,-1, 0,1,0};
        SkyCamera.Frame f = OrientationController.cameraFrame(northVertical, Surface.ROTATION_0, 0);
        near(1, f.right[0]); near(1, f.up[2]); near(1, f.forward[1]);
        SkyCamera.Point east = SkyCamera.project(new double[]{0.25,1,0}, f, 60, 1000, 500);
        assertTrue(east.x > 500);
    }

    public void testDisplayQuarterTurnsRemapScreenAxesOnly() {
        float[] northVertical = {1,0,0, 0,0,-1, 0,1,0};
        SkyCamera.Frame r90 = OrientationController.cameraFrame(northVertical, Surface.ROTATION_90, 0);
        near(1, r90.right[2]); near(-1, r90.up[0]); near(1, r90.forward[1]);
        SkyCamera.Frame r270 = OrientationController.cameraFrame(northVertical, Surface.ROTATION_270, 0);
        near(-1, r270.right[2]); near(1, r270.up[0]); near(1, r270.forward[1]);
    }

    public void testPositiveDeclinationRotatesMagneticNorthEastOfTrueNorth() {
        float[] flat = {1,0,0, 0,1,0, 0,0,1};
        SkyCamera.Frame corrected = OrientationController.cameraFrame(flat, Surface.ROTATION_0, 10);
        assertTrue(corrected.up[0] > 0);
        near(Math.cos(Math.toRadians(10)), corrected.up[1]);
    }

    public void testHeadingCorrectionAppliesAfterDeclination() {
        float[] flat = {1,0,0, 0,1,0, 0,0,1};
        SkyCamera.Frame corrected = OrientationController.cameraFrame(flat, Surface.ROTATION_0, 10, -15);
        near(Math.sin(Math.toRadians(-5)), corrected.up[0]);
        near(Math.cos(Math.toRadians(-5)), corrected.up[1]);
        near(Math.cos(Math.toRadians(-5)), corrected.right[0]);
    }

    public void testHeadingCorrectionLeavesVerticalComponentUnchanged() {
        float[] northVertical = {1,0,0, 0,0,-1, 0,1,0};
        SkyCamera.Frame corrected = OrientationController.cameraFrame(northVertical, Surface.ROTATION_0, 0, 90);
        near(1, corrected.up[2]);
        near(0, corrected.forward[2]);
        near(1, corrected.forward[0]);
    }

    public void testHeadingCorrectionRejectsNonFiniteValue() {
        try {
            OrientationController.cameraFrame(new float[]{1,0,0, 0,1,0, 0,0,1},
                    Surface.ROTATION_0, 0, Double.NaN);
            fail("Expected non-finite correction to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    public void testCameraFrameRejectsNonFiniteMatrix() {
        try {
            OrientationController.cameraFrame(new float[]{1,0,0, 0,Float.NaN,0, 0,0,1},
                    Surface.ROTATION_0, 0);
            fail("Expected non-finite matrix to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
