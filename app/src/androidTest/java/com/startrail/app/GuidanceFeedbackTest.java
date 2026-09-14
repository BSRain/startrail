package com.startrail.app;

import android.content.Context;
import android.test.InstrumentationTestCase;

import com.startrail.app.astro.Astronomy;
import com.startrail.app.astro.SkyCamera;
import com.startrail.app.astro.Star;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Collections;

/** State-machine checks for alignment haptic requests; these do not assert physical vibration. */
public final class GuidanceFeedbackTest extends InstrumentationTestCase {
    private SkyMapView view;
    private SkyScene scene;
    private Star above;
    private Star below;

    @Override protected void setUp() throws Exception {
        super.setUp();
        scene=SkyScene.create(Instant.parse("2026-09-08T12:00:00Z"),22.3193,114.1694);
        for(Star star:scene.objects){
            Astronomy.HorizontalPosition position=scene.positions.get(star.id);
            if(position.altitude>10&&above==null)above=star;
            if(position.altitude<0&&below==null)below=star;
        }
        assertNotNull(above);assertNotNull(below);
        Context context=getInstrumentation().getTargetContext();
        getInstrumentation().runOnMainSync(()->{
            view=new SkyMapView(context,scene.objects,Collections.emptyList());
            view.setScene(scene,22.3193,114.1694);
        });
    }

    public void testStableAlignmentRequestsOnceWhileStationary() throws Exception {
        configure(above,true,true,basisFor(above));
        tick(1000);assertEquals(-1,longField("lastAlignmentHaptic"));
        tick(1499);assertEquals(-1,longField("lastAlignmentHaptic"));
        tick(1500);assertEquals(1500,longField("lastAlignmentHaptic"));
        assertTrue(booleanField("alignmentHapticLatched"));
        tick(5000);assertEquals(1500,longField("lastAlignmentHaptic"));
    }

    public void testManualAndUnreliableModesSuppressRequests() throws Exception {
        configure(above,false,true,basisFor(above));
        tick(1000);tick(2000);assertEquals(-1,longField("lastAlignmentHaptic"));
        configure(above,true,false,basisFor(above));
        tick(3000);tick(4000);assertEquals(-1,longField("lastAlignmentHaptic"));
    }

    public void testBelowHorizonSuppressesRequest() throws Exception {
        configure(below,true,true,basisFor(below));
        tick(1000);tick(2000);
        assertEquals(-1,longField("lastAlignmentHaptic"));
        assertFalse(booleanField("alignmentHapticLatched"));
    }

    public void testLeaveAndReenterHonorsCooldown() throws Exception {
        SkyCamera.Basis aligned=basisFor(above);
        configure(above,true,true,aligned);
        tick(1000);tick(1500);assertEquals(1500,longField("lastAlignmentHaptic"));

        Astronomy.HorizontalPosition p=scene.positions.get(above.id);
        setSensor(SkyCamera.manualBasis(p.azimuth+30,p.altitude,0));
        tick(1600);assertFalse(booleanField("alignmentHapticLatched"));
        setSensor(aligned);setField("alignedSince",-1L);
        tick(1700);tick(2200);
        assertEquals(1500,longField("lastAlignmentHaptic"));
        assertFalse(booleanField("alignmentHapticLatched"));
        tick(3500);assertEquals(3500,longField("lastAlignmentHaptic"));
        assertTrue(booleanField("alignmentHapticLatched"));
    }

    private SkyCamera.Basis basisFor(Star star){
        Astronomy.HorizontalPosition p=scene.positions.get(star.id);
        return SkyCamera.manualBasis(p.azimuth,p.altitude,0);
    }

    private void configure(Star target,boolean tracking,boolean reliable,SkyCamera.Basis basis) throws Exception {
        onMain(()->{view.setGuidanceTarget(target);view.setTracking(tracking);view.setDirectionReliable(reliable);
            view.setSensorOrientation(basis.right,basis.up,basis.forward,"test");});
        setField("alignedSince",-1L);setField("lastAlignmentHaptic",-1L);setField("alignmentHapticLatched",false);
    }

    private void setSensor(SkyCamera.Basis basis) throws Exception {
        onMain(()->view.setSensorOrientation(basis.right,basis.up,basis.forward,"test"));
    }

    private void tick(long now) throws Exception {
        Method method=SkyMapView.class.getDeclaredMethod("updateAlignmentFeedback",long.class);
        method.setAccessible(true);onMain(()->{
            try{method.invoke(view,now);}catch(Exception error){throw new RuntimeException(error);}
        });
    }

    private long longField(String name) throws Exception{return (Long)getField(name).get(view);}
    private boolean booleanField(String name) throws Exception{return (Boolean)getField(name).get(view);}
    private void setField(String name,Object value) throws Exception{Field field=getField(name);field.set(view,value);}
    private static Field getField(String name) throws Exception{Field field=SkyMapView.class.getDeclaredField(name);field.setAccessible(true);return field;}
    private void onMain(Runnable action){getInstrumentation().runOnMainSync(action);}
}
