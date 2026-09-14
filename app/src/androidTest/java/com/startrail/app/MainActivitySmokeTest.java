package com.startrail.app;

import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Small end-to-end checks that launch the real Activity and exercise its programmatic UI. */
@SuppressWarnings("deprecation")
public final class MainActivitySmokeTest extends ActivityInstrumentationTestCase2<MainActivity> {
    public MainActivitySmokeTest() { super(MainActivity.class); }

    public void testLaunchAndNavigateToObservationList() throws Throwable {
        final MainActivity activity = getActivity();
        assertNotNull(findText(activity.getWindow().getDecorView(), "你的这一片星空"));

        final TextView observation = findText(activity.getWindow().getDecorView(), "◎  观测");
        assertNotNull(observation);
        runTestOnUiThread(observation::performClick);
        getInstrumentation().waitForIdleSync();
        assertNotNull(findText(activity.getWindow().getDecorView(), "现在，看什么"));
    }

    public void testChartRotationSurvivesPageRender() throws Throwable {
        final MainActivity activity = getActivity();
        final Field skyField = MainActivity.class.getDeclaredField("sky");
        skyField.setAccessible(true);
        final Method renderPage = MainActivity.class.getDeclaredMethod("renderPage");
        renderPage.setAccessible(true);

        runTestOnUiThread(() -> {
            try {
                SkyMapView before = (SkyMapView) skyField.get(activity);
                assertNotNull(before);
                before.setChartRotation(73.5f);
                renderPage.invoke(activity);
                SkyMapView after = (SkyMapView) skyField.get(activity);
                assertNotSame(before, after);
                assertEquals(73.5f, after.getChartRotation(), 0.001f);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
        getInstrumentation().waitForIdleSync();
    }

    public void testSearchDialogOpensWithoutCrash() throws Throwable {
        final MainActivity activity = getActivity();
        final Method search = MainActivity.class.getDeclaredMethod("search");
        search.setAccessible(true);
        runTestOnUiThread(() -> {
            try { search.invoke(activity); }
            catch (Exception exception) { throw new RuntimeException(exception); }
        });
        getInstrumentation().waitForIdleSync();
        android.app.UiAutomation automation = getInstrumentation().getUiAutomation();
        AccessibilityNodeInfo root = null;
        long deadline = android.os.SystemClock.uptimeMillis() + 5000;
        while (root == null && android.os.SystemClock.uptimeMillis() < deadline) {
            root = automation.getRootInActiveWindow();
            if (root == null) android.os.SystemClock.sleep(100);
        }
        assertNotNull("Accessibility window must be available after the dialog opens", root);
        List<AccessibilityNodeInfo> titles = root.findAccessibilityNodeInfosByText("寻找一颗星");
        assertNotNull(titles);
        assertFalse("Search dialog title should be visible", titles.isEmpty());
    }

    private static TextView findText(View view, String exact) {
        if (view instanceof TextView && exact.contentEquals(((TextView) view).getText())) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), exact);
                if (found != null) return found;
            }
        }
        return null;
    }

    public void testThreeDimensionalCameraRespondsToOrientation() throws Throwable {
        final MainActivity activity=getActivity();
        final Field field=MainActivity.class.getDeclaredField("sky");field.setAccessible(true);
        runTestOnUiThread(()->{try{
            SkyMapView view=(SkyMapView)field.get(activity);
            com.startrail.app.astro.SkyCamera.Frame east=com.startrail.app.astro.SkyCamera.manualFrame(90,30,20);
            view.setTracking(true);view.setSensorOrientation(east.right,east.up,east.forward,"test");
            assertEquals(90.0,view.getChartRotation(),0.01);assertEquals(30.0,view.getViewingAltitude(),0.01);
            com.startrail.app.astro.SkyCamera.Frame south=com.startrail.app.astro.SkyCamera.manualFrame(180,60,-10);
            view.setSensorOrientation(south.right,south.up,south.forward,"test");
            assertEquals(180.0,view.getChartRotation(),0.01);assertEquals(60.0,view.getViewingAltitude(),0.01);
        }catch(Exception e){throw new RuntimeException(e);}});
    }

    public void testSunMoonAndPlanetsShareSearchScene() throws Throwable {
        final MainActivity activity=getActivity();
        final Field field=MainActivity.class.getDeclaredField("scene");field.setAccessible(true);
        runTestOnUiThread(()->{try{
            SkyScene scene=(SkyScene)field.get(activity);
            assertEquals(92+com.startrail.app.astro.DeepSkyCatalog.entries().size(),scene.objects.size());assertEquals(9,scene.solar.size());
            assertNotNull(scene.find("sun"));assertNotNull(scene.find("moon"));assertNotNull(scene.find("saturn"));
            assertTrue(scene.find("moon").aliases.contains("月亮"));
            assertEquals(scene.solar.get("moon").altitude,scene.positions.get("moon").altitude,1e-10);
        }catch(Exception e){throw new RuntimeException(e);}});
    }
}
