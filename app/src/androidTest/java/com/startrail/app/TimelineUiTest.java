package com.startrail.app;

import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Regression checks for timeline playback state and its integration with the sky view. */
@SuppressWarnings("deprecation")
public final class TimelineUiTest extends ActivityInstrumentationTestCase2<MainActivity> {
    public TimelineUiTest() {
        super(MainActivity.class);
    }

    private Object get(MainActivity activity, String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(activity);
    }

    private void set(MainActivity activity, String name, Object value) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(activity, value);
    }

    private void call(MainActivity activity, String name) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(activity);
    }

    private void call(MainActivity activity, String name, int value) throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name, int.class);
        method.setAccessible(true);
        method.invoke(activity, value);
    }

    public void testPlaybackMovesBothDirectionsWithoutReplacingSky() throws Throwable {
        MainActivity activity = getActivity();
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(() -> {
            try {
                SkyMapView originalSky = (SkyMapView) get(activity, "sky");

                call(activity, "togglePlayback", 1);
                long forwardStart = (Long) get(activity, "fixedTime");
                set(activity, "playbackElapsed", SystemClock.elapsedRealtime() - 1_000L);
                ((Runnable) get(activity, "playbackTick")).run();
                assertTrue((Long) get(activity, "fixedTime") > forwardStart);
                assertSame(originalSky, get(activity, "sky"));

                call(activity, "togglePlayback", -1);
                long reverseStart = (Long) get(activity, "fixedTime");
                set(activity, "playbackElapsed", SystemClock.elapsedRealtime() - 1_000L);
                ((Runnable) get(activity, "playbackTick")).run();
                assertTrue((Long) get(activity, "fixedTime") < reverseStart);
                assertSame(originalSky, get(activity, "sky"));
                call(activity, "pausePlayback");
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    public void testPauseStopsPlaybackAndReturnToNowRestoresLiveMode() throws Throwable {
        MainActivity activity = getActivity();
        runTestOnUiThread(() -> {
            try {
                call(activity, "togglePlayback", 1);
                call(activity, "pausePlayback");
                assertEquals(0, ((Integer) get(activity, "playbackDirection")).intValue());
                long pausedTime = (Long) get(activity, "fixedTime");
                set(activity, "playbackElapsed", SystemClock.elapsedRealtime() - 5_000L);
                ((Runnable) get(activity, "playbackTick")).run();
                assertEquals(pausedTime, ((Long) get(activity, "fixedTime")).longValue());

                call(activity, "returnToNow");
                assertEquals(Boolean.TRUE, get(activity, "live"));
                assertEquals(0, ((Integer) get(activity, "playbackDirection")).intValue());
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    public void testLeavingSkyTabStopsPlayback() throws Throwable {
        MainActivity activity = getActivity();
        runTestOnUiThread(() -> {
            try {
                call(activity, "togglePlayback", 1);
                set(activity, "tab", 1);
                call(activity, "renderPage");
                assertEquals(0, ((Integer) get(activity, "playbackDirection")).intValue());

                set(activity, "tab", 0);
                call(activity, "renderPage");
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    public void testScheduledPlaybackAdvancesAndSamplesSkyRefreshCost() throws Throwable {
        MainActivity activity = getActivity();
        getInstrumentation().waitForIdleSync();
        final SkyMapView[] originalSky = new SkyMapView[1];
        final long[] startedAt = new long[1];
        runTestOnUiThread(() -> {
            try {
                originalSky[0] = (SkyMapView) get(activity, "sky");
                call(activity, "togglePlayback", 1);
                startedAt[0] = (Long) get(activity, "fixedTime");
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });

        // Sleep on the instrumentation thread so the main looper can execute scheduled ticks.
        SystemClock.sleep(600L);
        runTestOnUiThread(() -> {
            try {
                assertTrue((Long) get(activity, "fixedTime") > startedAt[0]);
                assertEquals(Boolean.FALSE, get(activity, "live"));
                assertSame(originalSky[0], get(activity, "sky"));
                call(activity, "pausePlayback");

                long startNanos = System.nanoTime();
                for (int i = 0; i < 10; i++) {
                    call(activity, "refreshSkyTime");
                }
                double meanMillis = (System.nanoTime() - startNanos) / 10.0 / 1_000_000.0;
                System.out.println("Sky refresh mean ms=" + meanMillis);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
    }

}
