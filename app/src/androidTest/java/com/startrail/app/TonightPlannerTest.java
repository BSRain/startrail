package com.startrail.app;

import com.startrail.app.astro.Astronomy;
import com.startrail.app.astro.SolarSystem;
import com.startrail.app.astro.Star;
import com.startrail.app.astro.StarCatalog;
import com.startrail.app.astro.TonightPlanner;
import com.startrail.app.astro.DeepSkyCatalog;

import java.time.Instant;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import junit.framework.TestCase;

public final class TonightPlannerTest extends TestCase {
    public void testDaySelectionFindsNextNightAndNeverSuggestsSun() {
        Instant selected = Instant.parse("2026-09-07T04:00:00Z");
        TonightPlanner.Result result = TonightPlanner.plan(selected, 22.3193, 114.1694);
        assertTrue(result.hasDarkWindow);
        assertTrue(result.windowStart.isAfter(selected));
        assertTrue(result.windowEnd.isAfter(result.windowStart));
        assertTrue(result.picks.size() <= 5);
        for (TonightPlanner.Pick pick : result.picks) {
            assertFalse("sun".equals(pick.starId));
            assertFalse("mercury".equals(pick.starId));
            assertFalse("uranus".equals(pick.starId));
            assertFalse("neptune".equals(pick.starId));
            assertFalse(pick.bestTime.isBefore(pick.windowStart));
            assertFalse(pick.bestTime.isAfter(pick.windowEnd));
            assertFalse(pick.windowStart.isBefore(result.windowStart));
            assertFalse(pick.windowEnd.isAfter(result.windowEnd));
        }
    }

    public void testNightSelectionStartsWithCurrentSample() {
        Instant selected = Instant.parse("2026-09-07T14:00:00Z");
        TonightPlanner.Result result = TonightPlanner.plan(selected, 22.3193, 114.1694);
        assertTrue(result.hasDarkWindow);
        assertEquals(selected, result.windowStart);
    }

    public void testPolarDayHasExplicitEmptyResult() {
        TonightPlanner.Result result = TonightPlanner.plan(
                Instant.parse("2026-06-21T12:00:00Z"), 89.0, 0.0);
        assertFalse(result.hasDarkWindow);
        assertNull(result.windowStart);
        assertNull(result.windowEnd);
        assertTrue(result.picks.isEmpty());
        assertTrue(result.message.contains("没有"));
    }

    public void testEveryPickMatchesCalculatedPosition() {
        double lat = 39.9042, lon = 116.4074;
        TonightPlanner.Result result = TonightPlanner.plan(
                Instant.parse("2026-01-15T10:00:00Z"), lat, lon);
        assertTrue(result.hasDarkWindow);
        assertFalse(result.picks.isEmpty());
        for (TonightPlanner.Pick pick : result.picks) {
            assertTrue(pick.altitudeDegrees >= 20.0);
            assertTrue(!pick.bestTime.isBefore(result.windowStart));
            assertTrue(!pick.bestTime.isAfter(result.windowEnd));
            Star star = StarCatalog.findById(pick.starId);
            DeepSkyCatalog.Entry deep = DeepSkyCatalog.find(pick.starId);
            if (star == null && deep != null) star = deep.star;
            double altitude, azimuth;
            if (star != null) {
                Astronomy.HorizontalPosition hp = Astronomy.horizontal(star, pick.bestTime, lat, lon);
                altitude = hp.altitudeDegrees; azimuth = hp.azimuthDegrees;
            } else {
                SolarSystem.BodyPosition body = null;
                for (SolarSystem.BodyPosition p : SolarSystem.snapshot(pick.bestTime, lat, lon))
                    if (p.id.equals(pick.starId)) body = p;
                assertNotNull(body);
                altitude = body.altitude; azimuth = body.azimuth;
            }
            assertEquals(altitude, pick.altitudeDegrees, 1e-9);
            assertEquals(azimuth, pick.azimuthDegrees, 1e-9);
            assertNotNull(pick.moonlightNote);
            assertTrue(pick.moonlightNote.contains("月球"));
            if ("moon".equals(pick.kind)) assertNotNull(pick.moonPhaseNote);
            else assertNull(pick.moonPhaseNote);

            // Every sampled point in the advertised interval remains observable.
            for (Instant time = pick.windowStart; !time.isAfter(pick.windowEnd);
                 time = time.plus(Duration.ofMinutes(TonightPlanner.SAMPLE_MINUTES))) {
                SolarSystem.BodyPosition sun = bodyAt("sun", time, lat, lon);
                assertNotNull(sun);
                assertTrue(sun.altitude <= -6.0);
                Star intervalStar = StarCatalog.findById(pick.starId);
                DeepSkyCatalog.Entry intervalDeep = DeepSkyCatalog.find(pick.starId);
                if (intervalStar == null && intervalDeep != null) intervalStar = intervalDeep.star;
                if ("deep_sky".equals(pick.kind)) assertTrue(sun.altitude <= -18.0);
                double intervalAltitude = intervalStar != null
                        ? Astronomy.horizontal(intervalStar, time, lat, lon).altitudeDegrees
                        : bodyAt(pick.starId, time, lat, lon).altitude;
                assertTrue(intervalAltitude >= 20.0);
            }
        }
    }

    public void testRecommendationsAreDistinctAndIncludeSolarSystemVarietyWhenAvailable() {
        double lat = 22.3193, lon = 114.1694;
        TonightPlanner.Result result = TonightPlanner.plan(
                Instant.parse("2026-09-07T10:00:00Z"), lat, lon);
        Set<String> ids = new HashSet<>();
        boolean pickedBody = false;
        for (TonightPlanner.Pick pick : result.picks) {
            assertTrue(ids.add(pick.starId));
            if ("moon".equals(pick.kind) || "planet".equals(pick.kind)) pickedBody = true;
        }
        boolean availableBody = false;
        for (Instant time = result.windowStart; !time.isAfter(result.windowEnd);
             time = time.plus(Duration.ofMinutes(TonightPlanner.SAMPLE_MINUTES))) {
            for (String id : new String[]{"moon", "venus", "mars", "jupiter", "saturn"}) {
                SolarSystem.BodyPosition body = bodyAt(id, time, lat, lon);
                if (body != null && body.altitude >= 20.0) availableBody = true;
            }
        }
        if (availableBody) assertTrue(pickedBody);
    }

    private static SolarSystem.BodyPosition bodyAt(String id, Instant time,
                                                   double latitude, double longitude) {
        for (SolarSystem.BodyPosition body : SolarSystem.snapshot(time, latitude, longitude))
            if (id.equals(body.id)) return body;
        return null;
    }
}
