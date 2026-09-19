package com.startrail.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.test.InstrumentationTestCase;

import com.startrail.app.astro.Star;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Renderer regression checks for the 4.2 observing view. */
@SuppressWarnings("deprecation")
public final class SkyExperience42Test extends InstrumentationTestCase {
    private SkyMapView map;
    private SkyScene scene;

    @Override protected void setUp() throws Exception {
        super.setUp();
        map=new SkyMapView(getInstrumentation().getTargetContext());
        map.layout(0,0,1080,1920);
        scene=SkyScene.create(Instant.parse("2026-09-19T14:00:00Z"),22.3193,114.1694);
        map.setScene(scene,22.3193,114.1694);
    }

    public void testLabelDensityIncreasesOnZoom() throws Exception {
        Method eligible=SkyMapView.class.getDeclaredMethod("eligibleLabel",Star.class);
        eligible.setAccessible(true);
        int wide=0,zoom=0;
        map.setManualView(180,35,95);
        for(Star star:scene.objects)if((Boolean)eligible.invoke(map,star))wide++;
        map.setManualView(180,35,30);
        for(Star star:scene.objects)if((Boolean)eligible.invoke(map,star))zoom++;
        assertTrue("zoom reveals more candidates",zoom>wide);
    }

    public void testDeepSkyLabelsJoinAtMediumZoom() throws Exception {
        Method eligible=SkyMapView.class.getDeclaredMethod("eligibleLabel",Star.class);
        eligible.setAccessible(true);
        Star deep=null;
        for(Star star:scene.objects)if(com.startrail.app.astro.DeepSkyCatalog.find(star.id)!=null&&star.magnitude>1.25){deep=star;break;}
        assertNotNull("catalog needs a faint deep-sky target",deep);
        map.setManualView(180,35,95);
        assertFalse((Boolean)eligible.invoke(map,deep));
        map.setManualView(180,35,55);
        assertTrue((Boolean)eligible.invoke(map,deep));
        map.setDeepSkyVisible(false);
        assertFalse((Boolean)eligible.invoke(map,deep));
    }

    public void testLabelsAvoidHudAndEachOther() throws Exception {
        map.setManualView(180,35,55);
        map.focus(scene.find("vega"));
        Bitmap bitmap=draw();
        List<RectF> labels=labels();
        assertTrue("view should contain eligible labels",labels.size()>0);
        bitmap.recycle();
        for(int i=0;i<labels.size();i++){
            RectF a=labels.get(i);
            assertTrue(a.left>=0&&a.right<=map.getWidth());
            assertTrue(a.top>=0&&a.bottom<=map.getHeight()-map.getResources().getDisplayMetrics().density*64);
            for(int j=i+1;j<labels.size();j++)assertFalse(RectF.intersects(a,labels.get(j)));
        }
    }

    public void testSelectedLabelRemainsWhenLabelsDisabled() throws Exception {
        Star target=scene.find("vega");
        map.focus(target);
        map.setLayers(true,false);
        Bitmap bitmap=draw();
        assertEquals(1,labels().size());
        bitmap.recycle();
        Field owners=SkyMapView.class.getDeclaredField("labelOwners");owners.setAccessible(true);
        assertEquals(target,((List<?>)owners.get(map)).get(0));
    }

    public void testSunMoonRedModeAndPhaseRender() throws Exception {
        map.focus(scene.find("moon"));
        Bitmap normal=draw();
        map.setRedMode(true);
        Bitmap red=draw();
        assertTrue("red mode changes body palette",differentPixels(normal,red)>1000);
        normal.recycle();red.recycle();
    }

    private Bitmap draw(){
        Bitmap bitmap=Bitmap.createBitmap(map.getWidth(),map.getHeight(),Bitmap.Config.ARGB_8888);
        map.draw(new Canvas(bitmap));return bitmap;
    }
    @SuppressWarnings("unchecked") private List<RectF> labels() throws Exception {
        Field field=SkyMapView.class.getDeclaredField("labels");field.setAccessible(true);
        return new ArrayList<>((List<RectF>)field.get(map));
    }
    private static int differentPixels(Bitmap a,Bitmap b){
        int count=0;
        for(int y=0;y<a.getHeight();y+=8)for(int x=0;x<a.getWidth();x+=8)
            if(a.getPixel(x,y)!=b.getPixel(x,y))count++;
        return count;
    }
}
