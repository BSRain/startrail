package com.startrail.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import com.startrail.app.astro.SkyCamera;

import java.time.Instant;

import junit.framework.TestCase;

/** Pixel checks for both optional sky decorations on transparent canvases. */
public final class DecorationOpacity42Test extends TestCase {
    private static final int WIDTH = 480, HEIGHT = 320;
    private static final Instant TIME = Instant.parse("2026-07-18T14:00:00Z");

    public void testConstellationOpacity() {
        ConstellationArtwork artwork = new ConstellationArtwork();
        artwork.update(TIME, 22.3, 114.2);
        checkArtwork(artwork);
        assertInvalidArtwork(artwork);
    }

    public void testGalaxyOpacity() {
        GalaxyBackground galaxy = new GalaxyBackground();
        galaxy.update(TIME, 22.3, 114.2);
        checkGalaxy(galaxy);
        assertInvalidGalaxy(galaxy);
    }

    private void checkArtwork(ConstellationArtwork artwork) {
        artwork.setOpacity(0f);
        int zero = maxArtworkAlpha(artwork);
        artwork.setOpacity(.5f);
        int half = maxArtworkAlpha(artwork);
        artwork.setOpacity(1f);
        int full = maxArtworkAlpha(artwork);
        assertEquals("zero opacity paints no pixels", 0, zero);
        assertTrue("half opacity remains visible", half > 0);
        assertTrue("half opacity is dimmer", half < full);
        artwork.setOpacity(-1f);
        assertEquals(0, maxArtworkAlpha(artwork));
        artwork.setOpacity(2f);
        assertEquals(full, maxArtworkAlpha(artwork));
    }

    private void checkGalaxy(GalaxyBackground galaxy) {
        galaxy.setOpacity(0f);
        int zero = maxGalaxyAlpha(galaxy);
        galaxy.setOpacity(.5f);
        int half = maxGalaxyAlpha(galaxy);
        galaxy.setOpacity(1f);
        int full = maxGalaxyAlpha(galaxy);
        assertEquals("zero opacity paints no pixels", 0, zero);
        assertTrue("half opacity remains visible", half > 0);
        assertTrue("half opacity is dimmer", half < full);
        galaxy.setOpacity(-1f);
        assertEquals(0, maxGalaxyAlpha(galaxy));
        galaxy.setOpacity(2f);
        assertEquals(full, maxGalaxyAlpha(galaxy));
    }

    private int maxArtworkAlpha(ConstellationArtwork artwork) {
        int max = 0;
        for (int azimuth = 0; azimuth < 360; azimuth += 30) {
            Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
            artwork.draw(new Canvas(bitmap), SkyCamera.manualBasis(azimuth, 35, 0),
                    72, WIDTH, HEIGHT, false, false);
            max = Math.max(max, maxAlpha(bitmap));
            bitmap.recycle();
        }
        return max;
    }

    private int maxGalaxyAlpha(GalaxyBackground galaxy) {
        int max = 0;
        for (int azimuth = 0; azimuth < 360; azimuth += 45) {
            Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
            galaxy.draw(new Canvas(bitmap), SkyCamera.manualBasis(azimuth, 35, 0),
                    72, WIDTH, HEIGHT, false, false);
            max = Math.max(max, maxAlpha(bitmap));
            bitmap.recycle();
        }
        return max;
    }

    private static int maxAlpha(Bitmap bitmap) {
        int[] pixels = new int[WIDTH * HEIGHT];
        bitmap.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT);
        int max = 0;
        for (int pixel : pixels) max = Math.max(max, pixel >>> 24);
        return max;
    }

    private void assertInvalidArtwork(ConstellationArtwork artwork) {
        for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            try { artwork.setOpacity(value); fail("invalid constellation opacity accepted"); }
            catch (IllegalArgumentException expected) { /* expected */ }
        }
    }

    private void assertInvalidGalaxy(GalaxyBackground galaxy) {
        for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            try { galaxy.setOpacity(value); fail("invalid galaxy opacity accepted"); }
            catch (IllegalArgumentException expected) { /* expected */ }
        }
    }
}
