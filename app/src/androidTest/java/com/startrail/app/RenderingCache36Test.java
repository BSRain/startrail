package com.startrail.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.test.ActivityInstrumentationTestCase2;
import com.startrail.app.astro.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Checks that reused render state follows time/location changes and keeps both hemispheres. */
@SuppressWarnings("deprecation")
public final class RenderingCache36Test extends ActivityInstrumentationTestCase2<MainActivity> {
    public RenderingCache36Test() { super(MainActivity.class); }
    private Object field(Object target, String name) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true); return f.get(target);
    }
    @SuppressWarnings("unchecked")
    public void testPreparedCatalogueSurvivesViewRebuild() throws Throwable {
        MainActivity a=getActivity();
        runTestOnUiThread(() -> {try {
            List<BackgroundStars.Entry> prepared=(List<BackgroundStars.Entry>)field(a,"renderBackgroundStars");
            assertSame(prepared,BackgroundStars.forRendering(prepared));
            SkyMapView original=(SkyMapView)field(a,"sky");
            assertSame(prepared,field(original,"backgroundStars"));
            java.lang.reflect.Method render=MainActivity.class.getDeclaredMethod("renderPage");render.setAccessible(true);render.invoke(a);
            assertNotSame(original,field(a,"sky"));
            assertSame(prepared,field(field(a,"sky"),"backgroundStars"));
            try { prepared.clear();fail("Catalogue must be immutable"); } catch (UnsupportedOperationException expected) { }
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    @SuppressWarnings("unchecked")
    public void testReusedBackgroundMatchesScalarPositionsAfterTimeAndLocationChange() throws Throwable {
        MainActivity a=getActivity();
        runTestOnUiThread(() -> {try {
            SkyMapView sky=(SkyMapView)field(a,"sky");
            List<BackgroundStars.Entry> stars=(List<BackgroundStars.Entry>)field(sky,"backgroundStars");
            List<double[]> points=(List<double[]>)field(sky,"backdrop");
            double[] first=points.get(0);
            double[][] locations={{39.9,116.4},{-33.8,-151.2}};
            for(int epoch=0;epoch<2;epoch++){
                Instant time=Instant.parse(epoch==0?"1900-01-02T03:00:00Z":"2100-12-30T21:00:00Z");
                double lat=locations[epoch][0],lon=locations[epoch][1];
                sky.setScene(SkyScene.create(time,lat,lon),lat,lon);
                assertSame(first,points.get(0));assertEquals(stars.size(),points.size());
                boolean above=false,below=false;
                for(int i=0;i<stars.size();i++){
                    BackgroundStars.Entry star=stars.get(i);double[] actual=points.get(i);
                    Astronomy.HorizontalPosition h=Astronomy.horizontal(star.raHours,star.decDegrees,time,lat,lon);
                    double[] expected=SkyCamera.horizontalVector(h.altitude,h.azimuth);
                    for(int j=0;j<3;j++)assertEquals(expected[j],actual[j],1e-9);
                    assertEquals(star.magnitude,actual[3],0.0);above|=actual[2]>0;below|=actual[2]<0;
                }
                assertTrue(above&&below);
            }
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    @SuppressWarnings("unchecked")
    public void testDrawReusesHitPointsAndLabelBoxes() throws Throwable {
        MainActivity a=getActivity();getInstrumentation().waitForIdleSync();
        runTestOnUiThread(() -> {try {
            SkyMapView sky=(SkyMapView)field(a,"sky");SkyScene scene=(SkyScene)field(a,"scene");
            sky.focus(scene.find("m31"));
            Bitmap bitmap=Bitmap.createBitmap(sky.getWidth(),sky.getHeight(),Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);
            sky.draw(canvas);Object hit=((Map<?,?>)field(sky,"hits")).get("m31");assertNotNull(hit);
            Object box=((List<?>)field(sky,"labelPool")).get(0);
            sky.draw(canvas);assertSame(hit,((Map<?,?>)field(sky,"hits")).get("m31"));assertSame(box,((List<?>)field(sky,"labelPool")).get(0));bitmap.recycle();
        }catch(Exception e){throw new RuntimeException(e);}});
    }
}
