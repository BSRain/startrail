package com.startrail.app;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.startrail.app.astro.CelestialEvents;

import java.io.File;
import java.io.FileOutputStream;
import java.time.Instant;
import java.util.Collections;

/** Deterministic screenshots and an end-to-end list/detail smoke test for the 4.3 event UI. */
@SuppressWarnings("deprecation")
public final class EventCalendarVisual43Test extends ActivityInstrumentationTestCase2<MainActivity> {
    private EventCalendarDialog controller;

    public EventCalendarVisual43Test() { super(MainActivity.class); }

    public void testLoadedListAndDeterministicDetailScreenshots() throws Throwable {
        MainActivity activity = getActivity();
        runTestOnUiThread(() -> {
            controller = new EventCalendarDialog(activity, new EventCalendarDialog.Actions() {
                public void preview(String id, Instant time) { }
                public void locate() { }
                public void configureWeather() { }
            });
            controller.show(22.3193, 114.1694, "香港");
        });

        waitForText("查看观测建议", 15000);
        capture(activity, "startrail-4.3-events.png");

        Instant now = Instant.now();
        CelestialEvents.Event fixture = new CelestialEvents.Event(
                "visual-new-moon", "未来 48 小时新月观测样本",
                "固定的无网络界面样本，用于核对地点、时区、观测建议和预约入口。",
                "https://github.com/cosinekitty/astronomy", CelestialEvents.Kind.MOON_PHASE,
                now.plusSeconds(24 * 3600), now, now.plusSeconds(48 * 3600),
                "moon", "", false, false, 0, Double.NaN, Double.NaN, Double.NaN,
                Collections.emptyList());
        runTestOnUiThread(() -> showDetail(fixture));
        waitForAnyText(10000, "推荐观测", "没有连续可用时段", "没有满足高度与天色条件");
        capture(activity, "startrail-4.3-event-detail.png");
    }

    private void showDetail(CelestialEvents.Event event) {
        try {
            java.lang.reflect.Method method = EventCalendarDialog.class.getDeclaredMethod(
                    "showDetail", CelestialEvents.Event.class, java.util.List.class, String.class);
            method.setAccessible(true);
            method.invoke(controller, event, Collections.emptyList(), null);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private View waitForText(String text, long timeoutMs) throws Exception {
        return waitForAnyText(timeoutMs, text);
    }

    private View waitForAnyText(long timeoutMs, String... choices) throws Exception {
        long deadline = android.os.SystemClock.uptimeMillis() + timeoutMs;
        do {
            getInstrumentation().waitForIdleSync();
            AlertDialog dialog = (AlertDialog) field(controller, "dialog");
            for (String choice : choices) {
                View found = find(dialog.getWindow().getDecorView(), choice);
                if (found != null) return found;
            }
            android.os.SystemClock.sleep(50);
        } while (android.os.SystemClock.uptimeMillis() < deadline);
        fail("timed out waiting for event UI");
        return null;
    }

    private View find(View view, String text) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(text)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private Object field(Object target, String name) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private void capture(MainActivity activity, String name) throws Exception {
        getInstrumentation().waitForIdleSync();
        Bitmap bitmap = getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File file = new File(activity.getExternalFilesDir(null), name);
        try (FileOutputStream out = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
        } finally { bitmap.recycle(); }
        assertTrue(file.isFile());
    }

    @Override protected void tearDown() throws Exception {
        if (controller != null) getInstrumentation().runOnMainSync(() -> controller.destroy());
        super.tearDown();
    }
}
