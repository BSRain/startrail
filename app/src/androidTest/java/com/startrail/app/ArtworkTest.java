package com.startrail.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import com.startrail.app.astro.SkyCamera;

import java.time.Instant;

import junit.framework.TestCase;

/** Rendering-level coverage for celestial artwork geometry and renderer isolation. */
public final class ArtworkTest extends TestCase {
    private static final int WIDTH = 480;
    private static final int HEIGHT = 320;

    public void testMetadataAndAllSkyRender() {
        ConstellationArtwork artwork = new ConstellationArtwork();
        assertEquals(7, ConstellationArtwork.count());
        assertEquals(7, ConstellationArtwork.names().length);
        artwork.update(Instant.parse("2026-07-18T14:00:00Z"), 22.3, 114.2);

        int painted = 0;
        for (int azimuth = 0; azimuth < 360; azimuth += 30) {
            Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
            artwork.draw(new Canvas(bitmap), SkyCamera.manualBasis(azimuth, 35, 0),
                    72, WIDTH, HEIGHT, false, false);
            painted += nonTransparentPixels(bitmap);
            bitmap.recycle();
        }
        assertTrue("at least one catalog-anchored figure should render across an all-sky sweep", painted > 200);
    }

    public void testInstancesDoNotOverwriteEachOthersTimeDependentProjection() {
        Instant firstTime = Instant.parse("2026-01-15T12:00:00Z");
        Instant secondTime = Instant.parse("2026-01-15T18:00:00Z");
        ConstellationArtwork first = new ConstellationArtwork();
        ConstellationArtwork second = new ConstellationArtwork();
        ConstellationArtwork reference = new ConstellationArtwork();
        first.update(firstTime, 51.5, -.1);
        second.update(secondTime, -33.9, 151.2);
        reference.update(firstTime, 51.5, -.1);

        SkyCamera.Basis camera = SkyCamera.manualBasis(0, 45, 0);
        Bitmap actual = render(first, camera);
        Bitmap expected = render(reference, camera);
        assertTrue("another renderer update must not mutate this instance", actual.sameAs(expected));
        actual.recycle();
        expected.recycle();
    }

    public void testNightDayRedAndRearPlaneViewsDoNotCrash() {
        ConstellationArtwork artwork = new ConstellationArtwork();
        artwork.update(Instant.parse("2026-10-03T02:30:00Z"), -23.5, -46.6);
        for (boolean red : new boolean[]{false, true}) {
            for (boolean day : new boolean[]{false, true}) {
                Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
                artwork.draw(new Canvas(bitmap), SkyCamera.manualBasis(210, -25, 18),
                        105, WIDTH, HEIGHT, red, day);
                bitmap.recycle();
            }
        }
    }

    public void testArtworkMovesWithSiderealTime() {
        ConstellationArtwork early = new ConstellationArtwork();
        ConstellationArtwork later = new ConstellationArtwork();
        early.update(Instant.parse("2026-04-09T00:00:00Z"), 35.7, 139.7);
        later.update(Instant.parse("2026-04-09T06:00:00Z"), 35.7, 139.7);
        boolean differs = false;
        for (int azimuth = 0; azimuth < 360 && !differs; azimuth += 45) {
            SkyCamera.Basis camera = SkyCamera.manualBasis(azimuth, 35, 0);
            Bitmap a = render(early, camera);
            Bitmap b = render(later, camera);
            differs = !a.sameAs(b);
            a.recycle();
            b.recycle();
        }
        assertTrue("equatorial artwork should move as sidereal time advances", differs);
    }

    private static Bitmap render(ConstellationArtwork artwork, SkyCamera.Basis camera) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        artwork.draw(new Canvas(bitmap), camera, 90, WIDTH, HEIGHT, false, false);
        return bitmap;
    }

    private static int nonTransparentPixels(Bitmap bitmap) {
        int[] pixels = new int[WIDTH * HEIGHT];
        bitmap.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT);
        int count = 0;
        for (int pixel : pixels) if ((pixel >>> 24) != 0) count++;
        return count;
    }
}
