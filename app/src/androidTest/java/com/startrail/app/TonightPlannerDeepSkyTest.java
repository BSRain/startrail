package com.startrail.app;

import com.startrail.app.astro.DeepSkyCatalog;
import com.startrail.app.astro.SolarSystem;
import com.startrail.app.astro.TonightPlanner;

import java.time.Instant;
import java.util.concurrent.CancellationException;

import junit.framework.TestCase;

public final class TonightPlannerDeepSkyTest extends TestCase {
    public void testQualifiedNightIncludesMessierDeepSkyTarget() {
        TonightPlanner.Result result = TonightPlanner.plan(
                Instant.parse("2026-01-15T10:00:00Z"), 22.3193, 114.1694);
        TonightPlanner.Pick deep = firstDeepSky(result);
        assertNotNull(deep);
        assertTrue(deep.starId.startsWith("m"));
        assertNotNull(DeepSkyCatalog.find(deep.starId));
        assertEquals(DeepSkyCatalog.find(deep.starId).equipment, deep.equipment);
        assertTrue(deep.altitudeDegrees >= 20.0);
        assertTrue(deep.reason.contains("不代表实际可见度"));
        assertTrue(bodyAt("sun", deep.bestTime, 22.3193, 114.1694).altitude <= -18.0);
    }

    public void testNauticalTwilightDoesNotForceDeepSkyTarget() {
        TonightPlanner.Result result = TonightPlanner.plan(
                Instant.parse("2026-06-21T00:00:00Z"), 60.0, 0.0);
        assertTrue(result.hasDarkWindow);
        assertNull(firstDeepSky(result));
    }

    public void testInterruptedPlanningCancelsAtEntry() {
        Thread.currentThread().interrupt();
        try {
            TonightPlanner.plan(Instant.parse("2026-01-15T10:00:00Z"), 0.0, 0.0);
            fail("Expected CancellationException");
        } catch (CancellationException expected) {
            // Expected boundary behavior.
        } finally {
            Thread.interrupted();
        }
    }

    public void testLatitudeAndLongitudeBoundariesRemainAccepted() {
        TonightPlanner.plan(Instant.parse("2026-01-15T10:00:00Z"), -90.0, -180.0);
        TonightPlanner.plan(Instant.parse("2026-01-15T10:00:00Z"), 90.0, 180.0);
    }

    private static TonightPlanner.Pick firstDeepSky(TonightPlanner.Result result) {
        for (TonightPlanner.Pick pick : result.picks)
            if ("deep_sky".equals(pick.kind)) return pick;
        return null;
    }

    private static SolarSystem.BodyPosition bodyAt(String id, Instant time,
                                                    double latitude, double longitude) {
        for (SolarSystem.BodyPosition body : SolarSystem.snapshot(time, latitude, longitude))
            if (id.equals(body.id)) return body;
        return null;
    }
}
