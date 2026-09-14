package com.startrail.app;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.startrail.app.astro.WeatherPlanner;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation")
public final class Weather40UiTest extends ActivityInstrumentationTestCase2<MainActivity> {
    private WeatherPlanDialog controller;
    public Weather40UiTest() { super(MainActivity.class); }
    private Object field(Object target,String name)throws Exception { java.lang.reflect.Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target); }
    private View find(View view,String text) {
        if(view instanceof TextView && ((TextView)view).getText().toString().contains(text)) return view;
        if(view instanceof ViewGroup) { ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){View v=find(g.getChildAt(i),text);if(v!=null)return v;} }
        return null;
    }
    private WeatherPlanDialog.Actions actions(final String[] seen) { return new WeatherPlanDialog.Actions(){
        public void preview(String id,Instant time){seen[0]=id;seen[1]=time.toString();}
        public void add(String id){seen[2]=id;}
        public void locate(){seen[3]="locate";}
    }; }
    public void testFixturePlanPreviewAndChecklistActions()throws Throwable {
        MainActivity activity=getActivity();final String[] seen=new String[4];
        Instant start=Instant.parse("2026-09-12T12:00:00Z"),end=start.plusSeconds(4*3600);
        String json="{\"metadata\":{\"attributions\":[\"测试天气样本 · 非实时预报\"]},\"hours\":[";
        for(int i=0;i<5;i++){if(i>0)json+=",";json+="{\"forecastTime\":\""+start.plusSeconds(i*3600)+"\",\"cloudCover\":0.1,\"humidity\":0.5,\"precipitation\":{\"amount\":{\"value\":0,\"unit\":\"mm\"}},\"wind\":{\"speed\":{\"value\":1,\"unit\":\"m/s\"}}}";}
        final QWeatherClient.Forecast forecast=QWeatherClient.parse(json+"]}",start);
        final WeatherPlanner.Result result=WeatherPlanner.plan(start,end,39.9,116.4,forecast.hours);
        assertFalse(result.picks.isEmpty());
        runTestOnUiThread(()->{try{
            controller=new WeatherPlanDialog(activity,actions(seen)); controller.show(39.9,116.4,"北京 · 测试样本");
            java.lang.reflect.Method cancel=WeatherPlanDialog.class.getDeclaredMethod("cancel");cancel.setAccessible(true);cancel.invoke(controller);
            java.lang.reflect.Method render=WeatherPlanDialog.class.getDeclaredMethod("render",WeatherPlanner.Result.class,QWeatherClient.Forecast.class,String.class,Instant.class,Instant.class);render.setAccessible(true);render.invoke(controller,result,forecast,"",start,end);
            AlertDialog dialog=(AlertDialog)field(controller,"dialog");View root=dialog.getWindow().getDecorView();
            assertNotNull(find(root,"测试天气样本"));assertNotNull(find(root,"云量"));find(root,"加入观测清单").performClick();
            assertEquals(result.picks.get(0).targetId,seen[2]);
        }catch(Exception e){throw new RuntimeException(e);}});
        capture(activity,"startrail-4.0-weather.png");
        runTestOnUiThread(()->{try{
            AlertDialog dialog=(AlertDialog)field(controller,"dialog");find(dialog.getWindow().getDecorView(),"预览这个时刻").performClick();
            assertEquals(result.picks.get(0).targetId,seen[0]);assertEquals(result.picks.get(0).bestTime.toString(),seen[1]);assertFalse(dialog.isShowing());
        }catch(Exception e){throw new RuntimeException(e);}});
    }
    public void testConfigurationHasNoPrivateKeyTextField()throws Throwable {
        MainActivity activity=getActivity();runTestOnUiThread(()->{
            controller=new WeatherPlanDialog(activity,actions(new String[4]));controller.configure();
        });getInstrumentation().waitForIdleSync();
        runTestOnUiThread(()->{try{
            AlertDialog dialog=(AlertDialog)field(controller,"configDialog");View root=dialog.getWindow().getDecorView();
            assertNotNull(find(root,"API Host"));assertNotNull(find(root,"导入 Ed25519 私钥文件"));assertNotNull(find(root,"无后台定位或请求"));
        }catch(Exception e){throw new RuntimeException(e);}});
        capture(activity,"startrail-4.0-jwt.png");
    }
    public void testClosingPlanCancelsAndDoesNotReopen()throws Throwable {
        MainActivity activity=getActivity();runTestOnUiThread(()->{try{
            controller=new WeatherPlanDialog(activity,actions(new String[4]));controller.show(39.9,116.4,"测试地点");((AlertDialog)field(controller,"dialog")).dismiss();
        }catch(Exception e){throw new RuntimeException(e);}});
        android.os.SystemClock.sleep(500);getInstrumentation().waitForIdleSync();
        assertFalse(((AlertDialog)field(controller,"dialog")).isShowing());
    }
    public void testPrivateKeyResultAfterControllerRecreation()throws Throwable {
        MainActivity activity=getActivity();
        String pem="-----BEGIN PRIVATE KEY-----\nMC4CAQAwBQYDK2VwBCIEIJ1hsZ3v/VpguoRK9JLsLMREScVpc7MmkXA7rAMcp/Zg\n-----END PRIVATE KEY-----\n";
        java.io.File file=new java.io.File(activity.getCacheDir(),"public-rfc8032-test.pem");
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){out.write(pem.getBytes(java.nio.charset.StandardCharsets.US_ASCII));}
        new QWeatherCredentials(activity).clearPrivateKey();
        new QWeatherCredentials(activity).saveConfig("test.qweatherapi.com","Q123456789","TESTPROJECT","TESTKEY");
        try {
            runTestOnUiThread(()->{try{
                java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod("onActivityResult",int.class,int.class,android.content.Intent.class);m.setAccessible(true);
                m.invoke(activity,WeatherPlanDialog.IMPORT_KEY,android.app.Activity.RESULT_OK,new android.content.Intent().setData(android.net.Uri.fromFile(file)));
                controller=(WeatherPlanDialog)field(activity,"weatherPlan");assertNotNull(controller);
            }catch(Exception e){throw new RuntimeException(e);}});
            for(int i=0;i<100&&!new QWeatherCredentials(activity).hasPrivateKey();i++)android.os.SystemClock.sleep(50);
            assertTrue(new QWeatherCredentials(activity).hasPrivateKey());
            assertEquals(3,new QWeatherCredentials(activity).signToken(Instant.now()).split("\\.").length);
        } finally { new QWeatherCredentials(activity).clearPrivateKey();file.delete(); }
    }
    @Override protected void tearDown()throws Exception {
        if(controller!=null)getInstrumentation().runOnMainSync(()->controller.destroy());super.tearDown();
    }
    private void capture(MainActivity a,String name)throws Exception {
        getInstrumentation().waitForIdleSync();Bitmap b=getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(b);
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(a.getExternalFilesDir(null),name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
    }
}
