package com.startrail.app;

import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import com.startrail.app.astro.Star;

/** Regression checks for user navigation, real-time guidance, and asynchronous recommendations. */
@SuppressWarnings("deprecation")
public final class ExperienceV3Test extends ActivityInstrumentationTestCase2<MainActivity> {
    public ExperienceV3Test(){super(MainActivity.class);}
    private Object get(MainActivity a,String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}
    private void set(MainActivity a,String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(a,value);}
    private void call(MainActivity a,String name)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(a);}
    private TextView text(View v,String value){if(v instanceof TextView&&value.contentEquals(((TextView)v).getText()))return (TextView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){TextView t=text(g.getChildAt(i),value);if(t!=null)return t;}}return null;}
    public void testFullscreenExpandsSkyAndBackRestoresNavigation()throws Throwable{
        MainActivity a=getActivity();getInstrumentation().waitForIdleSync();
        final int[] height=new int[1];
        runTestOnUiThread(()->{try{height[0]=((SkyMapView)get(a,"sky")).getHeight();TextView b=text(a.getWindow().getDecorView(),"全屏");assertNotNull(b);b.performClick();}catch(Exception e){throw new RuntimeException(e);}});
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->{try{assertEquals(View.GONE,((View)get(a,"nav")).getVisibility());assertTrue(((SkyMapView)get(a,"sky")).getHeight()>height[0]);a.onBackPressed();}catch(Exception e){throw new RuntimeException(e);}});
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->{try{assertEquals(View.VISIBLE,((View)get(a,"nav")).getVisibility());assertNotNull(text(a.getWindow().getDecorView(),"全屏"));}catch(Exception e){throw new RuntimeException(e);}});
    }
    public void testGuidanceReturnsToNowPersistsAcrossRenderAndEnds()throws Throwable{
        MainActivity a=getActivity();
        runTestOnUiThread(()->{try{
            set(a,"live",false);set(a,"fixedTime",Instant.parse("2000-01-01T00:00:00Z").toEpochMilli());
            Method m=MainActivity.class.getDeclaredMethod("startGuidance",Star.class);m.setAccessible(true);
            SkyScene scene=(SkyScene)get(a,"scene");m.invoke(a,scene.find("moon"));
            assertEquals(Boolean.TRUE,get(a,"live"));assertEquals("moon",((SkyMapView)get(a,"sky")).getGuidanceTarget().id);
            call(a,"renderPage");assertEquals("moon",((SkyMapView)get(a,"sky")).getGuidanceTarget().id);
            TextView end=text(a.getWindow().getDecorView(),"结束");assertNotNull(end);end.performClick();assertNull(((SkyMapView)get(a,"sky")).getGuidanceTarget());
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    public void testRecommendationsLoadAndAllowPreview()throws Throwable{
        MainActivity a=getActivity();
        runTestOnUiThread(()->{try{
            set(a,"live",false);set(a,"fixedTime",Instant.parse("2026-09-07T12:00:00Z").toEpochMilli());set(a,"latitude",39.9042);set(a,"longitude",116.4074);set(a,"tab",1);call(a,"renderPage");
        }catch(Exception e){throw new RuntimeException(e);}});
        final boolean[] ready={false};long deadline=android.os.SystemClock.uptimeMillis()+15000;
        while(!ready[0]&&android.os.SystemClock.uptimeMillis()<deadline){
            runTestOnUiThread(()->ready[0]=text(a.getWindow().getDecorView(),"查看时段与寻星  ›")!=null);
            if(!ready[0])android.os.SystemClock.sleep(100);
        }
        assertTrue("Recommendation cards must load asynchronously",ready[0]);
        runTestOnUiThread(()->{TextView c=text(a.getWindow().getDecorView(),"查看时段与寻星  ›");assertNotNull(c);assertTrue(((View)c.getParent()).performClick());});
        getInstrumentation().waitForIdleSync();
        android.view.accessibility.AccessibilityNodeInfo root=null;
        long dialogDeadline=android.os.SystemClock.uptimeMillis()+5000;
        while(android.os.SystemClock.uptimeMillis()<dialogDeadline){root=getInstrumentation().getUiAutomation().getRootInActiveWindow();if(root!=null&&!root.findAccessibilityNodeInfosByText("预览该时刻").isEmpty())break;android.os.SystemClock.sleep(100);}
        assertNotNull("Dialog accessibility window must become available",root);assertFalse("Recommendation preview action must be visible",root.findAccessibilityNodeInfosByText("预览该时刻").isEmpty());
    }
    public void testLiveRefreshKeepsCameraInstanceAndGuidance()throws Throwable{
        MainActivity a=getActivity();runTestOnUiThread(()->{try{
            SkyMapView before=(SkyMapView)get(a,"sky");set(a,"guidanceId","moon");before.setGuidanceTarget(((SkyScene)get(a,"scene")).find("moon"));
            call(a,"refreshSkyTime");assertSame(before,get(a,"sky"));assertEquals("moon",before.getGuidanceTarget().id);
        }catch(Exception e){throw new RuntimeException(e);}});
    }
}
