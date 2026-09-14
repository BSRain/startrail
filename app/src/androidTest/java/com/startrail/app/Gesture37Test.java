package com.startrail.app;

import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.view.InputDevice;
import android.view.MotionEvent;

import java.lang.reflect.Field;

/** Regression coverage for the 3.7 drag, scale, and pointer-handoff behavior. */
@SuppressWarnings("deprecation")
public final class Gesture37Test extends ActivityInstrumentationTestCase2<MainActivity> {
    public Gesture37Test() { super(MainActivity.class); }

    public void testPinchSwitchesToManualAndSurvivingPointerMovesWithoutJump() throws Throwable {
        MainActivity activity = getActivity();
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(() -> {
            SkyMapView sky = sky(activity);
            sky.setManualView(180, 35, 75);
            sky.setTracking(true);

            float cx = sky.getWidth() / 2f;
            float cy = sky.getHeight() / 2f;
            long downTime = SystemClock.uptimeMillis();
            send(sky, single(downTime, downTime, MotionEvent.ACTION_DOWN, 0, cx - 150, cy));
            send(sky, two(downTime, downTime + 10, MotionEvent.ACTION_POINTER_DOWN, 1,
                    cx - 150, cy, cx + 150, cy));
            send(sky, two(downTime, downTime + 20, MotionEvent.ACTION_MOVE, 0,
                    cx - 250, cy, cx + 250, cy));
            send(sky, two(downTime, downTime + 30, MotionEvent.ACTION_MOVE, 0,
                    cx - 400, cy, cx + 400, cy));

            assertTrue("A real pinch must change field of view", sky.getFieldOfView() < 70);
            assertFalse("Scaling must switch tracking to manual mode", sky.isTracking());

            send(sky, two(downTime, downTime + 40, MotionEvent.ACTION_POINTER_UP, 0,
                    cx - 400, cy, cx + 400, cy));
            float azimuthAtHandoff = sky.getChartRotation();
            double altitudeAtHandoff = sky.getViewingAltitude();
            send(sky, single(downTime, downTime + 50, MotionEvent.ACTION_MOVE, 1,
                    cx + 404, cy + 3));

            assertAngleDeltaAtMost(2.0, azimuthAtHandoff, sky.getChartRotation());
            assertEquals(altitudeAtHandoff, sky.getViewingAltitude(), 2.0);
            send(sky, single(downTime, downTime + 60, MotionEvent.ACTION_UP, 1,
                    cx + 404, cy + 3));
        });
    }

    public void testPinchDoesNotBecomeClickWhenLastFingerLifts() throws Throwable {
        MainActivity activity = getActivity();
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(() -> {
            SkyMapView sky = sky(activity);
            final int[] clicks = {0};
            sky.setOnClickListener(ignored -> clicks[0]++);

            float cx = sky.getWidth() / 2f;
            float cy = sky.getHeight() / 2f;
            long downTime = SystemClock.uptimeMillis();
            send(sky, single(downTime, downTime, MotionEvent.ACTION_DOWN, 0, cx, cy));
            send(sky, two(downTime, downTime + 10, MotionEvent.ACTION_POINTER_DOWN, 1,
                    cx, cy, cx + 90, cy));
            send(sky, two(downTime, downTime + 20, MotionEvent.ACTION_MOVE, 0,
                    cx, cy, cx + 140, cy));
            send(sky, two(downTime, downTime + 30, MotionEvent.ACTION_POINTER_UP, 1,
                    cx, cy, cx + 140, cy));
            send(sky, single(downTime, downTime + 40, MotionEvent.ACTION_UP, 0, cx, cy));

            assertEquals("A completed scale gesture must not become a click", 0, clicks[0]);
        });
    }

    public void testDragChangesAzimuthAndClampsAltitudeToFiniteRange() throws Throwable {
        MainActivity activity = getActivity();
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(() -> {
            SkyMapView sky = sky(activity);
            sky.setManualView(2, 89, 75);
            sky.setTracking(true);
            float cx = sky.getWidth() / 2f;
            float cy = sky.getHeight() / 2f;
            long downTime = SystemClock.uptimeMillis();
            send(sky, single(downTime, downTime, MotionEvent.ACTION_DOWN, 0, cx, cy));
            send(sky, single(downTime, downTime + 20, MotionEvent.ACTION_MOVE, 0,
                    cx + sky.getWidth() * 2f, cy + sky.getHeight() * 2f));
            send(sky, single(downTime, downTime + 30, MotionEvent.ACTION_UP, 0,
                    cx + sky.getWidth() * 2f, cy + sky.getHeight() * 2f));

            assertFalse("Dragging must switch tracking to manual mode", sky.isTracking());
            assertTrue("Azimuth must remain normalized", sky.getChartRotation() >= 0
                    && sky.getChartRotation() < 360);
            assertTrue("Altitude must remain finite and clamped", Double.isFinite(sky.getViewingAltitude())
                    && sky.getViewingAltitude() <= 89.8 && sky.getViewingAltitude() >= -89.8);
            assertEquals(89.8, sky.getViewingAltitude(), 0.01);
        });
    }

    private static SkyMapView sky(MainActivity activity) {
        try {
            return (SkyMapView) field(activity, "sky");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Object field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static MotionEvent single(long downTime, long eventTime, int action, int id,
                                      float x, float y) {
        return event(downTime, eventTime, action, 0,
                new int[]{id}, new float[]{x}, new float[]{y});
    }

    private static MotionEvent two(long downTime, long eventTime, int action, int actionIndex,
                                   float x0, float y0, float x1, float y1) {
        return event(downTime, eventTime, action, actionIndex,
                new int[]{0, 1}, new float[]{x0, x1}, new float[]{y0, y1});
    }

    private static MotionEvent event(long downTime, long eventTime, int action, int actionIndex,
                                     int[] ids, float[] xs, float[] ys) {
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[ids.length];
        MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[ids.length];
        for (int i = 0; i < ids.length; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            properties[i].id = ids[i];
            properties[i].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coordinates[i] = new MotionEvent.PointerCoords();
            coordinates[i].x = xs[i];
            coordinates[i].y = ys[i];
            coordinates[i].pressure = 1;
            coordinates[i].size = 1;
        }
        int encodedAction = action;
        if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
            encodedAction |= actionIndex << MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        }
        return MotionEvent.obtain(downTime, eventTime, encodedAction, ids.length,
                properties, coordinates, 0, 0, 1, 1, 0, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0);
    }

    private static void send(SkyMapView sky, MotionEvent event) {
        try {
            assertTrue(sky.onTouchEvent(event));
        } finally {
            event.recycle();
        }
    }

    private static void assertAngleDeltaAtMost(double maximum, double expected, double actual) {
        double delta = Math.abs(actual - expected) % 360;
        delta = Math.min(delta, 360 - delta);
        assertTrue("Pointer handoff jumped by " + delta + " degrees", delta <= maximum);
    }
}
