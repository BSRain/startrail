package com.startrail.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation")
public final class AppearanceSettings42Test extends ActivityInstrumentationTestCase2<MainActivity> {
    private SharedPreferences prefs;
    private float oldGalaxy,oldArtwork;
    public AppearanceSettings42Test(){super(MainActivity.class);}
    @Override protected void setUp()throws Exception{
        super.setUp();prefs=getInstrumentation().getTargetContext().getSharedPreferences("settings",Context.MODE_PRIVATE);
        oldGalaxy=prefs.getFloat("galaxyOpacity",.8f);oldArtwork=prefs.getFloat("artworkOpacity",.7f);
        prefs.edit().putFloat("galaxyOpacity",.8f).putFloat("artworkOpacity",.7f).commit();
    }
    @Override protected void tearDown()throws Exception{
        SharedPreferences restore=prefs;float galaxy=oldGalaxy,artwork=oldArtwork;
        super.tearDown();restore.edit().putFloat("galaxyOpacity",galaxy).putFloat("artworkOpacity",artwork).commit();
    }
    private Object field(Object value,String name)throws Exception{
        Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);
    }
    private AlertDialog open(MainActivity activity)throws Exception{
        Method m=MainActivity.class.getDeclaredMethod("settings");m.setAccessible(true);return (AlertDialog)m.invoke(activity);
    }
    private void sliders(View view,List<SeekBar> result){
        if(view instanceof SeekBar)result.add((SeekBar)view);
        if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)sliders(g.getChildAt(i),result);}
    }
    private float actual(MainActivity a,String layer)throws Exception{return (Float)field(field(field(a,"sky"),layer),"opacity");}
    public void testPreviewCancelAndApply()throws Throwable{
        MainActivity a=getActivity();final AlertDialog[] dialog={null};
        runTestOnUiThread(()->{try{
            dialog[0]=open(a);List<SeekBar> bars=new ArrayList<>();sliders(dialog[0].getWindow().getDecorView(),bars);assertEquals(2,bars.size());
            bars.get(0).setProgress(25);bars.get(1).setProgress(45);
            assertEquals(.25f,actual(a,"galaxy"),.001f);assertEquals(.45f,actual(a,"artwork"),.001f);
            assertEquals(.8f,prefs.getFloat("galaxyOpacity",0),.001f);
            dialog[0].getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        }catch(Exception e){throw new RuntimeException(e);}});
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->{try{
            assertEquals(.8f,actual(a,"galaxy"),.001f);assertEquals(.7f,actual(a,"artwork"),.001f);
            dialog[0]=open(a);List<SeekBar> bars=new ArrayList<>();sliders(dialog[0].getWindow().getDecorView(),bars);
            bars.get(0).setProgress(0);bars.get(1).setProgress(100);dialog[0].getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        }catch(Exception e){throw new RuntimeException(e);}});
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->{try{
            assertEquals(0f,actual(a,"galaxy"),0f);assertEquals(1f,actual(a,"artwork"),0f);
            assertEquals(0f,prefs.getFloat("galaxyOpacity",-1),0f);assertEquals(1f,prefs.getFloat("artworkOpacity",-1),0f);
            dialog[0]=open(a);List<SeekBar> bars=new ArrayList<>();sliders(dialog[0].getWindow().getDecorView(),bars);
            assertEquals(0,bars.get(0).getProgress());assertEquals(100,bars.get(1).getProgress());dialog[0].dismiss();
        }catch(Exception e){throw new RuntimeException(e);}});
    }
}
