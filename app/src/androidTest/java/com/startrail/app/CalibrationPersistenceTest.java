package com.startrail.app;

import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Exercises the real calibration dialog, save/cancel transactions, and Activity recreation. */
@SuppressWarnings("deprecation")
public final class CalibrationPersistenceTest extends ActivityInstrumentationTestCase2<MainActivity> {
    private SharedPreferences settings;
    private float original;
    private MainActivity recreated;
    public CalibrationPersistenceTest(){super(MainActivity.class);}
    @Override protected void setUp()throws Exception{super.setUp();settings=getInstrumentation().getTargetContext().getSharedPreferences("settings",Context.MODE_PRIVATE);original=settings.getFloat("headingCorrection",0);settings.edit().putFloat("headingCorrection",0).commit();}
    @Override protected void tearDown()throws Exception{SharedPreferences restore=settings;float restoreValue=original;if(recreated!=null){getInstrumentation().runOnMainSync(recreated::finish);getInstrumentation().waitForIdleSync();}super.tearDown();if(restore!=null)restore.edit().putFloat("headingCorrection",restoreValue).commit();}
    private Object field(MainActivity a,String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}
    private AlertDialog open(MainActivity a)throws Exception{Method m=MainActivity.class.getDeclaredMethod("calibrationHelp");m.setAccessible(true);m.invoke(a);return (AlertDialog)field(a,"calibrationDialog");}
    private SeekBar seek(View v){if(v instanceof SeekBar)return (SeekBar)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){SeekBar found=seek(g.getChildAt(i));if(found!=null)return found;}}return null;}
    private void assertOffset(MainActivity a,double expected)throws Exception{assertEquals(expected,(Double)field(a,"headingCorrection"),1e-9);assertEquals(expected,((OrientationController)field(a,"orientation")).getHeadingCorrection(),1e-9);}
    private interface UiWork{void run()throws Exception;}
    private void ui(UiWork action)throws Throwable{runTestOnUiThread(()->{try{action.run();}catch(Exception e){throw new RuntimeException(e);}});getInstrumentation().waitForIdleSync();}
    public void testSaveSurvivesActivityRecreation()throws Throwable{
        MainActivity a=getActivity();
        ui(()->{AlertDialog dialog=open(a);seek(dialog.getWindow().getDecorView()).setProgress(37);dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});
        ui(()->{assertOffset(a,7);assertEquals(7f,settings.getFloat("headingCorrection",0),0f);});
        Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(MainActivity.class.getName(),null,false);
        try{ui(a::recreate);recreated=(MainActivity)getInstrumentation().waitForMonitorWithTimeout(monitor,10000);assertNotNull("Recreated Activity must launch",recreated);getInstrumentation().waitForIdleSync();
            ui(()->{assertOffset(recreated,7);AlertDialog dialog=open(recreated);assertEquals(37,seek(dialog.getWindow().getDecorView()).getProgress());dialog.dismiss();});
        }finally{getInstrumentation().removeMonitor(monitor);}
    }
    public void testCancelRollsBackPreviewWithoutSaving()throws Throwable{
        MainActivity a=getActivity();
        ui(()->{AlertDialog dialog=open(a);seek(dialog.getWindow().getDecorView()).setProgress(18);assertEquals(-12,((OrientationController)field(a,"orientation")).getHeadingCorrection(),1e-9);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();});
        ui(()->{assertOffset(a,0);assertEquals(0f,settings.getFloat("headingCorrection",99),0f);AlertDialog reopened=open(a);assertEquals(30,seek(reopened.getWindow().getDecorView()).getProgress());reopened.dismiss();});
    }
    public void testDismissRollsBackAndZeroCanBeSaved()throws Throwable{
        MainActivity a=getActivity();
        ui(()->{AlertDialog dialog=open(a);seek(dialog.getWindow().getDecorView()).setProgress(60);dialog.dismiss();});
        ui(()->{assertOffset(a,0);AlertDialog second=open(a);seek(second.getWindow().getDecorView()).setProgress(0);second.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});
        ui(()->{assertOffset(a,-30);AlertDialog reset=open(a);seek(reset.getWindow().getDecorView()).setProgress(30);reset.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});
        ui(()->{assertOffset(a,0);assertEquals(0f,settings.getFloat("headingCorrection",99),0f);});
    }
}
