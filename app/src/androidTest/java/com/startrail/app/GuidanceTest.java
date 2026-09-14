package com.startrail.app;

import com.startrail.app.astro.Guidance;
import com.startrail.app.astro.SkyCamera;
import junit.framework.TestCase;

/** Pure direction fixtures for the target-guidance overlay. */
public final class GuidanceTest extends TestCase {
    private static final double EPS=1e-6;

    public void testForwardTargetIsAligned() {
        SkyCamera.Basis camera=SkyCamera.manualBasis(0,30,0);
        Guidance.Result result=Guidance.calculate(SkyCamera.horizontalVector(30,0),camera,2);
        assertEquals(0,result.angularSeparation,EPS);
        assertTrue(result.aligned);
        assertFalse(result.belowHorizon);
    }

    public void testCameraRelativeRightAndLeft() {
        SkyCamera.Basis camera=SkyCamera.manualBasis(0,0,0);
        Guidance.Result right=Guidance.calculate(SkyCamera.horizontalVector(0,45),camera,2);
        Guidance.Result left=Guidance.calculate(SkyCamera.horizontalVector(0,315),camera,2);
        assertTrue(right.screenX>.99);assertEquals(0,right.screenY,EPS);
        assertTrue(left.screenX<-.99);assertEquals(0,left.screenY,EPS);
    }

    public void testRollChangesScreenDirection() {
        SkyCamera.Basis rolled=new SkyCamera.Basis(new double[]{0,0,1},new double[]{-1,0,0},new double[]{0,1,0});
        Guidance.Result east=Guidance.calculate(new double[]{1,1,0},rolled,2);
        assertEquals(0,east.screenX,EPS);
        assertTrue(east.screenY>.99);
    }

    public void testAntipodalTargetHasFiniteFallbackArrow() {
        SkyCamera.Basis camera=SkyCamera.manualBasis(0,0,0);
        Guidance.Result behind=Guidance.calculate(new double[]{0,-1,0},camera,2);
        assertEquals(180,behind.angularSeparation,EPS);
        assertTrue(Double.isFinite(behind.screenX));assertTrue(Double.isFinite(behind.screenY));
        assertEquals(0,behind.screenX,EPS);assertEquals(-1,behind.screenY,EPS);
    }

    public void testBelowHorizonCanNeverBeAligned() {
        SkyCamera.Basis camera=SkyCamera.manualBasis(0,-20,0);
        Guidance.Result result=Guidance.calculate(SkyCamera.horizontalVector(-20,0),camera,2);
        assertEquals(0,result.angularSeparation,EPS);
        assertTrue(result.belowHorizon);assertFalse(result.aligned);
    }

    public void testRejectsInvalidDirections() {
        SkyCamera.Basis camera=SkyCamera.manualBasis(0,0,0);
        assertInvalid(null,camera);assertInvalid(new double[]{0,0,0},camera);
        assertInvalid(new double[]{Double.NaN,0,1},camera);
    }

    public void testChineseCameraRelativeCues() {
        assertEquals("向右转动手机",Guidance.directionCue(1,0,false));
        assertEquals("向左转动手机",Guidance.directionCue(-1,0,false));
        assertEquals("向上转动手机",Guidance.directionCue(0,-1,false));
        assertEquals("向下转动手机",Guidance.directionCue(0,1,false));
        assertEquals("向左拖动星图",Guidance.directionCue(1,0,true));
        assertEquals("向右拖动星图",Guidance.directionCue(-1,0,true));
        assertEquals("向下拖动星图",Guidance.directionCue(0,-1,true));
        assertEquals("向上拖动星图",Guidance.directionCue(0,1,true));
    }

    private static void assertInvalid(double[] target,SkyCamera.Basis camera) {
        try { Guidance.calculate(target,camera,2);fail("Expected invalid direction rejection"); }
        catch (IllegalArgumentException expected) { /* expected */ }
    }
}
