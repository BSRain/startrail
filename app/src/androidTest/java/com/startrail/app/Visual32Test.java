package com.startrail.app;

import android.test.ActivityInstrumentationTestCase2;
import android.graphics.Bitmap;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;

/** Reproducible visual fixtures; captures are written to the app's external files directory. */
@SuppressWarnings("deprecation")
public final class Visual32Test extends ActivityInstrumentationTestCase2<MainActivity>{
    public Visual32Test(){super(MainActivity.class);}
    private void set(MainActivity a,String key,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(key);f.setAccessible(true);f.set(a,value);}
    private Object get(MainActivity a,String key)throws Exception{Field f=MainActivity.class.getDeclaredField(key);f.setAccessible(true);return f.get(a);}
    private void capture(String name)throws Exception{
        getInstrumentation().waitForIdleSync();android.os.SystemClock.sleep(200);
        Bitmap b=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(b);
        File file=new File(getActivity().getExternalFilesDir(null),name);
        try(FileOutputStream out=new FileOutputStream(file)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
    }
    public void testVisualFixtures()throws Throwable{
        MainActivity a=getActivity();
        runTestOnUiThread(()->{try{
            set(a,"live",false);set(a,"fixedTime",Instant.parse("2026-09-08T13:00:00Z").toEpochMilli());
            set(a,"latitude",39.9042);set(a,"longitude",116.4074);set(a,"followPhone",false);set(a,"redMode",false);
            set(a,"showGalaxy",true);set(a,"showArtwork",true);set(a,"showGrid",false);set(a,"timelineExpanded",true);set(a,"immersive",true);
            Method m=MainActivity.class.getDeclaredMethod("renderPage");m.setAccessible(true);m.invoke(a);
            SkyMapView sky=(SkyMapView)get(a,"sky");SkyScene scene=(SkyScene)get(a,"scene");sky.focus(scene.find("sadr"));
        }catch(Exception e){throw new RuntimeException(e);}});
        capture("startrail-3.2-cygnus.png");
        runTestOnUiThread(()->{try{
            set(a,"fixedTime",Instant.parse("2026-12-15T14:00:00Z").toEpochMilli());
            Method m=MainActivity.class.getDeclaredMethod("refreshSkyTime");m.setAccessible(true);m.invoke(a);
            ((SkyMapView)get(a,"sky")).focus(((SkyScene)get(a,"scene")).find("alnilam"));
        }catch(Exception e){throw new RuntimeException(e);}});
        capture("startrail-3.2-orion.png");
    }
}
