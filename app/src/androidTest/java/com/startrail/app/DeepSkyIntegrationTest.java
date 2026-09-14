package com.startrail.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PointF;
import android.test.ActivityInstrumentationTestCase2;
import android.view.MotionEvent;
import com.startrail.app.astro.*;
import java.lang.reflect.*;
import java.time.Instant;
import java.util.Map;

@SuppressWarnings("deprecation")
public final class DeepSkyIntegrationTest extends ActivityInstrumentationTestCase2<MainActivity>{
    public DeepSkyIntegrationTest(){super(MainActivity.class);}
    private Object get(Object o,String key)throws Exception{Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}
    private void set(Object o,String key,Object v)throws Exception{Field f=o.getClass().getDeclaredField(key);f.setAccessible(true);f.set(o,v);}
    private void prepare(MainActivity a)throws Exception{set(a,"live",false);set(a,"fixedTime",Instant.parse("2026-09-09T15:00:00Z").toEpochMilli());set(a,"latitude",39.9);set(a,"longitude",116.4);set(a,"followPhone",false);set(a,"immersive",true);set(a,"tab",0);set(a,"selected",null);set(a,"guidanceId",null);Method m=MainActivity.class.getDeclaredMethod("renderPage");m.setAccessible(true);m.invoke(a);}
    @SuppressWarnings("unchecked")
    public void testSceneLayerSelectionAndClick()throws Throwable{
        MainActivity a=getActivity();runTestOnUiThread(()->{try{prepare(a);}catch(Exception e){throw new RuntimeException(e);}});getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->{try{SkyMapView sky=(SkyMapView)get(a,"sky");SkyScene scene=(SkyScene)get(a,"scene");Star m31=scene.find("m31");assertNotNull(m31);assertEquals(92+DeepSkyCatalog.entries().size(),scene.objects.size());
            Bitmap b=Bitmap.createBitmap(sky.getWidth(),sky.getHeight(),Bitmap.Config.ARGB_8888);sky.focus(m31);sky.setDeepSkyVisible(true);sky.draw(new Canvas(b));assertTrue(((Map<?,?>)get(sky,"hits")).containsKey("m31"));
            sky.setSelected(null);sky.setDeepSkyVisible(false);sky.draw(new Canvas(b));assertFalse(((Map<?,?>)get(sky,"hits")).containsKey("m31"));
            sky.setSelected(m31);sky.draw(new Canvas(b));assertTrue(((Map<?,?>)get(sky,"hits")).containsKey("m31"));
            final String[] clicked={null};sky.setListener(star->clicked[0]=star.id);PointF point=((Map<String,PointF>)get(sky,"hits")).get("m31");long t=android.os.SystemClock.uptimeMillis();MotionEvent d=MotionEvent.obtain(t,t,0,point.x,point.y,0),u=MotionEvent.obtain(t,t+20,1,point.x,point.y,0);sky.onTouchEvent(d);sky.onTouchEvent(u);d.recycle();u.recycle();assertEquals("m31",clicked[0]);b.recycle();
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    public void testDeepTargetsKeepBothHemispheresAndTimeChanges(){
        boolean above=false,below=false;double first=Double.NaN;for(int hour=0;hour<24;hour+=3){SkyScene s=SkyScene.create(Instant.parse("2026-09-09T00:00:00Z").plusSeconds(hour*3600L),39.9,116.4);assertNotNull(s.find("m42"));double altitude=s.positions.get("m42").altitude;above|=altitude>0;below|=altitude<0;if(hour==0)first=altitude;if(hour==12)assertTrue(Math.abs(first-altitude)>5);}assertTrue(above&&below);
    }
    public void testSymbolKindsProduceDistinctVisibleImages(){
        Bitmap previous=null;for(String kind:new String[]{"galaxy","open_cluster","globular_cluster","nebula","planetary_nebula"}){Bitmap b=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888);DeepSkyRenderer.draw(new Canvas(b),kind,40,40,3,false);int count=0;for(int y=0;y<80;y++)for(int x=0;x<80;x++)if((b.getPixel(x,y)>>>24)>0)count++;assertTrue(count>30);if(previous!=null){assertFalse(previous.sameAs(b));previous.recycle();}previous=b;}if(previous!=null)previous.recycle();
    }
    public void testCaptureCatalogAndSky()throws Throwable{
        MainActivity a=getActivity();runTestOnUiThread(()->{try{prepare(a);SkyScene scene=(SkyScene)get(a,"scene");Star target=scene.find("m31");set(a,"selected",target);((SkyMapView)get(a,"sky")).focus(target);}catch(Exception e){throw new RuntimeException(e);}});capture(a,"startrail-3.5-deepsky.png");
        runTestOnUiThread(()->{try{Method m=MainActivity.class.getDeclaredMethod("showDeepSkyCatalog");m.setAccessible(true);m.invoke(a);}catch(Exception e){throw new RuntimeException(e);}});capture(a,"startrail-3.5-catalog.png");
    }
    public void testCatalogFiltersAndNumberSearch()throws Throwable{
        MainActivity a=getActivity();runTestOnUiThread(()->{try{prepare(a);Method m=MainActivity.class.getDeclaredMethod("showDeepSkyCatalog");m.setAccessible(true);m.invoke(a);}catch(Exception e){throw new RuntimeException(e);}});
        assertNotNull(node("全部 · 14 个目标"));
        assertTrue(node("星系").performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));getInstrumentation().waitForIdleSync();assertNotNull(node("星系 · 4 个目标"));
        assertTrue(node("星团").performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));getInstrumentation().waitForIdleSync();assertNotNull(node("星团 · 5 个目标"));
        assertTrue(node("星云").performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));getInstrumentation().waitForIdleSync();assertNotNull(node("星云 · 5 个目标"));
        assertTrue(node("关闭").performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->{try{Method m=MainActivity.class.getDeclaredMethod("search");m.setAccessible(true);m.invoke(a);}catch(Exception e){throw new RuntimeException(e);}});getInstrumentation().waitForIdleSync();
        android.view.accessibility.AccessibilityNodeInfo input=waitForInput();assertNotNull(input);
        android.os.Bundle args=new android.os.Bundle();args.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"NGC224");assertTrue(input.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,args));getInstrumentation().waitForIdleSync();
        assertNotNull(node("找到 1 个"));assertNotNull(node("Andromeda Galaxy"));
    }
    private android.view.accessibility.AccessibilityNodeInfo waitForInput(){
        for(int attempt=0;attempt<30;attempt++){
            android.view.accessibility.AccessibilityNodeInfo input=findInput(getInstrumentation().getUiAutomation().getRootInActiveWindow());
            if(input!=null)return input;
            android.os.SystemClock.sleep(100);
        }
        return null;
    }
    private android.view.accessibility.AccessibilityNodeInfo findInput(android.view.accessibility.AccessibilityNodeInfo n){
        if(n==null)return null;if("android.widget.EditText".contentEquals(n.getClassName()))return n;for(int i=0;i<n.getChildCount();i++){android.view.accessibility.AccessibilityNodeInfo result=findInput(n.getChild(i));if(result!=null)return result;}return null;
    }
    private android.view.accessibility.AccessibilityNodeInfo node(String text){
        for(int i=0;i<30;i++){android.view.accessibility.AccessibilityNodeInfo root=getInstrumentation().getUiAutomation().getRootInActiveWindow();if(root!=null){java.util.List<android.view.accessibility.AccessibilityNodeInfo> found=root.findAccessibilityNodeInfosByText(text);for(android.view.accessibility.AccessibilityNodeInfo n:found)if(text.contentEquals(n.getText()==null?"":n.getText()))return n;if(!found.isEmpty())return found.get(0);}android.os.SystemClock.sleep(100);}return null;
    }
    private void capture(MainActivity a,String name)throws Exception{getInstrumentation().waitForIdleSync();android.os.SystemClock.sleep(250);Bitmap b=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(b);try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(a.getExternalFilesDir(null),name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
}
