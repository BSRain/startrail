package com.startrail.app;
import android.test.ActivityInstrumentationTestCase2;
import android.graphics.*;
import android.os.SystemClock;
import java.time.Instant;
import java.util.*;

@SuppressWarnings("deprecation")
public final class Motion37Test extends ActivityInstrumentationTestCase2<MainActivity>{
    public Motion37Test(){super(MainActivity.class);}
    private Object get(Object o,String name)throws Exception{java.lang.reflect.Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private void set(Object o,String name,Object value)throws Exception{java.lang.reflect.Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    public void testMotionEndpointsAndReverse()throws Throwable{
        MainActivity a=getActivity();runTestOnUiThread(()->{try{
            SkyMapView sky=(SkyMapView)get(a,"sky");Instant t=Instant.parse("2026-09-09T15:00:00Z");
            sky.setPlaybackMotion(false);sky.setScene(SkyScene.create(t,39.9,116.4),39.9,116.4);sky.setPlaybackMotion(true);
            sky.setScene(SkyScene.create(t.plusSeconds(432),39.9,116.4),39.9,116.4);long start=(Long)get(sky,"motionStarted");
            assertEquals(-432,sky.motionOffsetAt(start),1e-8);assertEquals(-216,sky.motionOffsetAt(start+60),1e-8);assertEquals(0,sky.motionOffsetAt(start+120),0);
            sky.setPlaybackMotion(false);sky.setScene(SkyScene.create(t,39.9,116.4),39.9,116.4);sky.setPlaybackMotion(true);
            sky.setScene(SkyScene.create(t.minusSeconds(432),39.9,116.4),39.9,116.4);start=(Long)get(sky,"motionStarted");assertEquals(216,sky.motionOffsetAt(start+60),1e-8);
            sky.setPlaybackMotion(false);assertEquals(0,sky.motionOffsetAt(start),0);
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    @SuppressWarnings("unchecked")
    public void testIntermediateFrameMovesHitWithoutNewSceneAndLabelsAreClickable()throws Throwable{
        MainActivity a=getActivity();getInstrumentation().waitForIdleSync();runTestOnUiThread(()->{try{
            SkyMapView sky=(SkyMapView)get(a,"sky");Instant t=Instant.parse("2026-09-09T15:00:00Z");
            sky.setPlaybackMotion(false);sky.setScene(SkyScene.create(t,39.9,116.4),39.9,116.4);sky.focus(((SkyScene)get(sky,"scene")).find("m31"));sky.setPlaybackMotion(true);
            SkyScene next=SkyScene.create(t.plusSeconds(432),39.9,116.4);sky.setScene(next,39.9,116.4);
            Bitmap bitmap=Bitmap.createBitmap(sky.getWidth(),sky.getHeight(),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(bitmap);
            set(sky,"motionStarted",SystemClock.uptimeMillis()+20);sky.draw(c);PointF p=((Map<String,PointF>)get(sky,"hits")).get("m31");float x=p.x,y=p.y;
            set(sky,"motionStarted",SystemClock.uptimeMillis()-120);sky.draw(c);p=((Map<String,PointF>)get(sky,"hits")).get("m31");assertTrue(Math.hypot(p.x-x,p.y-y)>1);assertSame(next,get(sky,"scene"));
            List<RectF> boxes=(List<RectF>)get(sky,"labels");List<com.startrail.app.astro.Star> owners=(List<com.startrail.app.astro.Star>)get(sky,"labelOwners");assertFalse(boxes.isEmpty());
            RectF box=boxes.get(0);String id=owners.get(0).id;final String[] selected={null};sky.setListener(star->selected[0]=star.id);
            long now=SystemClock.uptimeMillis();android.view.MotionEvent d=android.view.MotionEvent.obtain(now,now,0,box.centerX(),box.centerY(),0),u=android.view.MotionEvent.obtain(now,now+20,1,box.centerX(),box.centerY(),0);sky.onTouchEvent(d);sky.onTouchEvent(u);d.recycle();u.recycle();assertEquals(id,selected[0]);
            sky.setPlaybackMotion(false);bitmap.recycle();
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    public void testPauseCommitsDisplayedTime()throws Throwable{
        MainActivity a=getActivity();runTestOnUiThread(()->{try{
            java.lang.reflect.Method toggle=MainActivity.class.getDeclaredMethod("togglePlayback",int.class);toggle.setAccessible(true);toggle.invoke(a,1);
            set(a,"playbackElapsed",SystemClock.elapsedRealtime()-120);((Runnable)get(a,"playbackTick")).run();
            SkyMapView sky=(SkyMapView)get(a,"sky");long before=sky.displayedTimeMillis();
            java.lang.reflect.Method pause=MainActivity.class.getDeclaredMethod("pausePlayback");pause.setAccessible(true);pause.invoke(a);
            assertFalse((Boolean)get(sky,"playbackMotion"));assertEquals(0,((Integer)get(a,"playbackDirection")).intValue());
            assertTrue(Math.abs((Long)get(a,"fixedTime")-before)<10000);assertEquals((Long)get(a,"fixedTime"),(Long)((SkyScene)get(a,"scene")).time.toEpochMilli());
        }catch(Exception e){throw new RuntimeException(e);}});
    }
}
