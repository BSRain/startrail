package com.startrail.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.test.ActivityInstrumentationTestCase2;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;

/** Repeatable CPU samples on a software canvas, not a device GPU/FPS benchmark. */
@SuppressWarnings("deprecation")
public final class Performance36Test extends ActivityInstrumentationTestCase2<MainActivity> {
    public Performance36Test() { super(MainActivity.class); }
    public void testMeasureSceneUpdateAndDraw() throws Throwable {
        MainActivity activity = getActivity();
        getInstrumentation().waitForIdleSync();
        final String[] report = new String[1];
        runTestOnUiThread(() -> {
            try {
                java.lang.reflect.Field field = MainActivity.class.getDeclaredField("sky");
                field.setAccessible(true);
                SkyMapView sky = (SkyMapView) field.get(activity);
                sky.setTracking(false);
                sky.setManualView(75, 40, 75);
                sky.setDecorations(true, true, false);
                sky.setLayers(true, true);
                sky.setRedMode(false);
                Bitmap bitmap = Bitmap.createBitmap(540, 960, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                canvas.scale(540f / sky.getWidth(), 960f / sky.getHeight());
                int samples = 40;
                double[] sceneCost = new double[samples], updateCost = new double[samples], drawCost = new double[samples];
                Instant start = Instant.parse("2026-09-09T15:00:00Z");
                for (int i = -10; i < samples; i++) {
                    long a = System.nanoTime();
                    SkyScene scene = SkyScene.create(start.plusSeconds(i * 144L), 39.9, 116.4);
                    long b = System.nanoTime();
                    sky.setScene(scene, 39.9, 116.4);
                    long c = System.nanoTime();
                    sky.draw(canvas);
                    long d = System.nanoTime();
                    if (i >= 0) { sceneCost[i] = (b-a)/1e6; updateCost[i] = (c-b)/1e6; drawCost[i] = (d-c)/1e6; }
                }
                bitmap.recycle();
                report[0] = "40 samples after 10 warmups; API " + android.os.Build.VERSION.SDK_INT
                    + "; software Canvas 540x960; manual camera 75/40, FOV75; galaxy/artwork/labels enabled\n"
                    + stats("scene", sceneCost) + stats("view_update", updateCost) + stats("draw", drawCost);
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(activity.getExternalFilesDir(null), "performance36.txt"))) {
            out.write(report[0].getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        System.out.println(report[0]);
    }
    private static String stats(String name, double[] samples) {
        double total = 0; for (double value : samples) total += value;
        Arrays.sort(samples);
        return String.format(Locale.ROOT, "%s: mean=%.3f ms, median=%.3f ms, p95=%.3f ms\n", name, total/samples.length, (samples[19]+samples[20])/2, samples[37]);
    }
}
