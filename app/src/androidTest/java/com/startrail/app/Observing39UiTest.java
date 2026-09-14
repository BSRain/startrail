package com.startrail.app;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.SeekBar;
import android.widget.CheckBox;
import com.startrail.app.astro.*;
import java.time.Instant;

@SuppressWarnings("deprecation")
public final class Observing39UiTest extends ActivityInstrumentationTestCase2<MainActivity>{
    private String saved;
    public Observing39UiTest(){super(MainActivity.class);}
    @Override protected void setUp()throws Exception{super.setUp();android.content.SharedPreferences p=getInstrumentation().getTargetContext().getSharedPreferences("observing_list",0);saved=p.getString("entries",null);p.edit().clear().commit();}
    @Override protected void tearDown()throws Exception{android.content.SharedPreferences p=getInstrumentation().getTargetContext().getSharedPreferences("observing_list",0);if(saved==null)p.edit().clear().commit();else p.edit().putString("entries",saved).commit();super.tearDown();}
    private Object field(Object o,String name)throws Exception{java.lang.reflect.Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private void set(Object o,String name,Object value)throws Exception{java.lang.reflect.Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
    private void call(MainActivity a,String name,Class<?>[] types,Object...args)throws Exception{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(a,args);}
    private void prepare(MainActivity a)throws Exception{set(a,"live",false);set(a,"fixedTime",Instant.parse("2026-09-11T12:00:00Z").toEpochMilli());set(a,"latitude",39.9);set(a,"longitude",116.4);set(a,"followPhone",false);set(a,"tab",0);call(a,"renderPage",new Class<?>[0]);}
    private View find(View root,Class<?> type,String text){if(type.isInstance(root)&&(text==null||root instanceof TextView&&((TextView)root).getText().toString().equals(text)))return root;if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),type,text);if(found!=null)return found;}}return null;}
    private void waitForProfile(MainActivity a)throws Throwable{for(int i=0;i<100;i++){final boolean[] ready={false};runTestOnUiThread(()->{try{AlertDialog dialog=(AlertDialog)field(a,"eventsDialog");ready[0]=dialog!=null&&find(dialog.getWindow().getDecorView(),AltitudeChartView.class,null)!=null;}catch(Exception e){throw new RuntimeException(e);}});if(ready[0])return;android.os.SystemClock.sleep(100);}fail("Profile did not finish computing");}
    public void testChartSelectAndPreviewUsesChosenTime()throws Throwable{
        MainActivity a=getActivity();runTestOnUiThread(()->{try{prepare(a);call(a,"showObservingProfile",new Class<?>[]{Star.class},((SkyScene)field(a,"scene")).find("m31"));}catch(Exception e){throw new RuntimeException(e);}});waitForProfile(a);
        capture(a,"startrail-3.9-profile.png");
        final long[] expected={0};runTestOnUiThread(()->{try{AlertDialog dialog=(AlertDialog)field(a,"eventsDialog");View root=dialog.getWindow().getDecorView();AltitudeChartView chart=(AltitudeChartView)find(root,AltitudeChartView.class,null);ObservingEvents.Result result=(ObservingEvents.Result)field(chart,"result");assertEquals(145,result.samples.size());
            SeekBar seek=(SeekBar)find(root,SeekBar.class,null);seek.setProgress(67);expected[0]=result.samples.get(67).time.toEpochMilli();View preview=find(root,TextView.class,"预览选中时刻");assertNotNull(preview);preview.performClick();assertEquals(expected[0],((SkyScene)field(a,"scene")).time.toEpochMilli());assertEquals("m31",((Star)field(a,"selected")).id);assertFalse(dialog.isShowing());
        }catch(Exception e){throw new RuntimeException(e);}});
        getInstrumentation().waitForIdleSync();assertNull(field(a,"eventsDialog"));
    }
    public void testObservingListCanCompleteAndRemoveFromUi()throws Throwable{
        MainActivity a=getActivity();runTestOnUiThread(()->{try{prepare(a);ObservingListStore store=(ObservingListStore)field(a,"observingList");store.add("m31");call(a,"showObservingList",new Class<?>[0]);}catch(Exception e){throw new RuntimeException(e);}});getInstrumentation().waitForIdleSync();
        android.view.accessibility.AccessibilityNodeInfo node=waitText("仙女座星系");assertNotNull(node);assertTrue(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));getInstrumentation().waitForIdleSync();assertTrue(new ObservingListStore(a).all().get(0).completed);capture(a,"startrail-3.9-checklist.png");
        node=waitText("移除");assertNotNull(node);assertTrue(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));getInstrumentation().waitForIdleSync();assertTrue(new ObservingListStore(a).all().isEmpty());
    }
    public void testDismissedCalculationCannotReopenDialog()throws Throwable{
        MainActivity a=getActivity();runTestOnUiThread(()->{try{prepare(a);call(a,"showObservingProfile",new Class<?>[]{Star.class},((SkyScene)field(a,"scene")).find("moon"));((AlertDialog)field(a,"eventsDialog")).dismiss();}catch(Exception e){throw new RuntimeException(e);}});android.os.SystemClock.sleep(400);getInstrumentation().waitForIdleSync();assertNull(field(a,"eventsDialog"));
    }
    private android.view.accessibility.AccessibilityNodeInfo waitText(String value){for(int i=0;i<50;i++){android.view.accessibility.AccessibilityNodeInfo root=getInstrumentation().getUiAutomation().getRootInActiveWindow();if(root!=null){java.util.List<android.view.accessibility.AccessibilityNodeInfo> found=root.findAccessibilityNodeInfosByText(value);for(android.view.accessibility.AccessibilityNodeInfo n:found)if(value.contentEquals(n.getText()==null?"":n.getText()))return n;}android.os.SystemClock.sleep(100);}return null;}
    private void capture(MainActivity a,String name)throws Exception{getInstrumentation().waitForIdleSync();android.os.SystemClock.sleep(250);Bitmap b=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(b);try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(a.getExternalFilesDir(null),name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
}
