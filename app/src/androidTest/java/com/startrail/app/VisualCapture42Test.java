package com.startrail.app;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;

import com.startrail.app.astro.Astronomy;
import com.startrail.app.astro.Star;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;

/** Fixed Hong Kong sky and settings visual fixtures for the 4.2 observing experience. */
@SuppressWarnings("deprecation")
public final class VisualCapture42Test extends ActivityInstrumentationTestCase2<MainActivity> {
    private static final double LATITUDE=22.3193, LONGITUDE=114.1694;
    // 21:00 Hong Kong time in winter: Orion is high and the Moon fixture is reproducible.
    private static final Instant TIME=Instant.parse("2026-01-25T13:00:00Z");

    public VisualCapture42Test(){super(MainActivity.class);}

    public void testCaptureSkyMoonAndSettings() throws Throwable {
        MainActivity activity=getActivity();
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->{
            try{
                field(activity,"latitude",LATITUDE);
                field(activity,"longitude",LONGITUDE);
                field(activity,"place","香港 · 视觉回归");
                field(activity,"live",false);
                field(activity,"fixedTime",TIME.toEpochMilli());
                field(activity,"followPhone",false);
                field(activity,"tab",0);
                call(activity,"renderPage");
                SkyMapView sky=sky(activity);
                SkyScene scene=(SkyScene)value(activity,"scene");
                Star betelgeuse=scene.find("betelgeuse"),rigel=scene.find("rigel");
                assertNotNull(betelgeuse);assertNotNull(rigel);
                Astronomy.HorizontalPosition shoulder=scene.positions.get(betelgeuse.id);
                Astronomy.HorizontalPosition foot=scene.positions.get(rigel.id);
                assertTrue("Orion fixture should be above the horizon",shoulder.altitude>0&&foot.altitude>0);
                sky.setTracking(false);
                sky.setManualView(shoulder.azimuth,(shoulder.altitude+foot.altitude)/2,75);
                sky.setDecorations(true,true,false);
                sky.setDecorationOpacity(.8f,.7f);
                sky.setLayers(true,true);
            }catch(Exception e){throw new RuntimeException(e);}
        });
        getInstrumentation().waitForIdleSync();
        save(activity,sky(activity),"startrail-4.2-sky.png");

        runTestOnUiThread(()->{
            try{
                SkyMapView sky=sky(activity);
                SkyScene scene=(SkyScene)value(activity,"scene");
                Star moon=scene.find("moon");assertNotNull(moon);
                sky.focus(moon);
                sky.setManualView(scene.positions.get(moon.id).azimuth,
                        scene.positions.get(moon.id).altitude,35);
            }catch(Exception e){throw new RuntimeException(e);}
        });
        getInstrumentation().waitForIdleSync();
        save(activity,sky(activity),"startrail-4.2-moon.png");

        final AlertDialog[] dialog=new AlertDialog[1];
        runTestOnUiThread(()->{
            try{dialog[0]=(AlertDialog)call(activity,"settings");}
            catch(Exception e){throw new RuntimeException(e);}
        });
        getInstrumentation().waitForIdleSync();
        assertNotNull(dialog[0]);
        save(activity,dialog[0].getWindow().getDecorView(),"startrail-4.2-settings.png");
        runTestOnUiThread(()->dialog[0].dismiss());
    }

    private SkyMapView sky(MainActivity activity) throws Exception{
        SkyMapView sky=(SkyMapView)value(activity,"sky");assertNotNull(sky);return sky;
    }
    private void save(MainActivity activity,View view,String name) throws Exception{
        int width=view.getWidth(),height=view.getHeight();
        assertTrue(name+" must have a measured width",width>0);
        assertTrue(name+" must have a measured height",height>0);
        Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        try{
            getInstrumentation().runOnMainSync(()->view.draw(new Canvas(bitmap)));
            File output=new File(activity.getExternalFilesDir(null),name);
            try(FileOutputStream stream=new FileOutputStream(output)){
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));
            }
            assertTrue(output.length()>0);
        }finally{bitmap.recycle();}
    }
    private static Object value(MainActivity activity,String name) throws Exception{
        Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);return field.get(activity);
    }
    private static void field(MainActivity activity,String name,Object value) throws Exception{
        Field field=MainActivity.class.getDeclaredField(name);field.setAccessible(true);field.set(activity,value);
    }
    private static Object call(MainActivity activity,String name) throws Exception{
        Method method=MainActivity.class.getDeclaredMethod(name);method.setAccessible(true);return method.invoke(activity);
    }
}
