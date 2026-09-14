package com.startrail.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.test.ActivityInstrumentationTestCase2;
import android.view.MotionEvent;
import com.startrail.app.astro.Star;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@SuppressWarnings("deprecation")
public final class BelowHorizonTest extends ActivityInstrumentationTestCase2<MainActivity>{
    public BelowHorizonTest(){super(MainActivity.class);}
    private Object get(Object object,String key)throws Exception{Field f=object.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(object);}
    private void set(Object object,String key,Object value)throws Exception{Field f=object.getClass().getDeclaredField(key);f.setAccessible(true);f.set(object,value);}
    private void prepare(MainActivity a)throws Exception{
        set(a,"live",false);set(a,"fixedTime",Instant.parse("2026-09-08T13:00:00Z").toEpochMilli());set(a,"latitude",39.9042);set(a,"longitude",116.4074);
        set(a,"followPhone",false);set(a,"immersive",true);set(a,"tab",0);set(a,"guidanceId",null);
        Method render=MainActivity.class.getDeclaredMethod("renderPage");render.setAccessible(true);render.invoke(a);
    }
    @SuppressWarnings("unchecked")
    public void testBelowHorizonStarsAndSolarBodiesAreRenderedAndClickable()throws Throwable{
        MainActivity a=getActivity();runTestOnUiThread(()->{try{prepare(a);}catch(Exception e){throw new RuntimeException(e);}});getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->{try{
            SkyMapView sky=(SkyMapView)get(a,"sky");SkyScene scene=(SkyScene)get(a,"scene");int checkedStars=0,checkedSolar=0;
            Bitmap bitmap=Bitmap.createBitmap(sky.getWidth(),sky.getHeight(),Bitmap.Config.ARGB_8888);
            for(Star star:scene.objects){if(scene.positions.get(star.id).altitude>=0)continue;
                sky.focus(star);sky.draw(new Canvas(bitmap));Map<String,PointF> hits=(Map<String,PointF>)get(sky,"hits");assertTrue("Below-horizon target must render: "+star.id,hits.containsKey(star.id));
                if(scene.solar.containsKey(star.id))checkedSolar++;else checkedStars++;
            }
            assertTrue(checkedStars>0);assertTrue(checkedSolar>0);
            Star sun=scene.find("sun");assertTrue(scene.positions.get("sun").altitude<0);sky.focus(sun);sky.draw(new Canvas(bitmap));
            final String[] tapped={null};sky.setListener(s->tapped[0]=s.id);long now=android.os.SystemClock.uptimeMillis();float x=sky.getWidth()/2f,y=sky.getHeight()/2f;
            MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(now,now+20,MotionEvent.ACTION_UP,x,y,0);
            sky.onTouchEvent(down);sky.onTouchEvent(up);down.recycle();up.recycle();assertEquals("sun",tapped[0]);bitmap.recycle();
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    @SuppressWarnings("unchecked")
    public void testBackgroundCatalogKeepsBothHemispheres()throws Throwable{
        MainActivity a=getActivity();runTestOnUiThread(()->{try{prepare(a);SkyMapView sky=(SkyMapView)get(a,"sky");List<double[]> points=(List<double[]>)get(sky,"backdrop");List<?> catalog=(List<?>)get(sky,"backgroundStars");assertEquals(catalog.size(),points.size());boolean below=false,above=false;for(double[] p:points){below|=p[2]<0;above|=p[2]>0;}assertTrue(below&&above);}catch(Exception e){throw new RuntimeException(e);}});
    }
    public void testCaptureBelowHorizonView()throws Throwable{
        MainActivity a=getActivity();runTestOnUiThread(()->{try{prepare(a);SkyScene scene=(SkyScene)get(a,"scene");Star sun=scene.find("sun");set(a,"selected",sun);((SkyMapView)get(a,"sky")).focus(sun);}catch(Exception e){throw new RuntimeException(e);}});
        getInstrumentation().waitForIdleSync();android.os.SystemClock.sleep(250);Bitmap image=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(image);
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(a.getExternalFilesDir(null),"startrail-3.4-below-horizon.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
    }
}
