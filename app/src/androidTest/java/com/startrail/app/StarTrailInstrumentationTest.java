package com.startrail.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.test.InstrumentationTestCase;

import com.startrail.app.astro.Astronomy;
import com.startrail.app.astro.ConstellationLine;
import com.startrail.app.astro.SkyProjection;
import com.startrail.app.astro.Star;
import com.startrail.app.astro.StarCatalog;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Device-level checks using only Android's built-in instrumentation APIs. */
public final class StarTrailInstrumentationTest extends InstrumentationTestCase {
    private Context context;

    @Override protected void setUp() throws Exception {
        super.setUp();
        context = getInstrumentation().getTargetContext();
        context.getSharedPreferences("journal", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Override protected void tearDown() throws Exception {
        context.getSharedPreferences("journal", Context.MODE_PRIVATE).edit().clear().commit();
        super.tearDown();
    }

    public void testJulianDateAndIndependentTransitReference() {
        assertEquals(2440587.5, Astronomy.julianDate(Instant.EPOCH), 1e-10);
        Instant j2000 = Instant.parse("2000-01-01T12:00:00Z");
        assertEquals(2451545.0, Astronomy.julianDate(j2000), 1e-10);

        // USNO's J2000 GMST reference is 18.697374558 h. A dec=0 object at
        // that RA transits the zenith at Greenwich on Earth's equator.
        Astronomy.HorizontalPosition transit =
                Astronomy.horizontal(18.697374558, 0, j2000, 0, 0);
        assertEquals(90.0, transit.altitudeDegrees, 0.0001);
    }

    public void testPoleGeometryAndPolarisSanityCheck() {
        Instant j2000 = Instant.parse("2000-01-01T12:00:00Z");
        Astronomy.HorizontalPosition pole = Astronomy.horizontal(6, 23.5, j2000, 90, 0);
        assertEquals(23.5, pole.altitudeDegrees, 1e-9);

        Star polaris = StarCatalog.findById("polaris");
        assertNotNull(polaris);
        Astronomy.HorizontalPosition hongKong = Astronomy.horizontal(
                polaris, Instant.parse("2026-09-06T12:00:00Z"), 22.3193, 114.1694);
        assertTrue(hongKong.azimuthDegrees <= 3 || hongKong.azimuthDegrees >= 357);
        assertEquals(22.3193, hongKong.altitudeDegrees, 1.2);
    }

    public void testCatalogueCoordinatesIdsAndLinesAreConsistent() {
        List<Star> stars = StarCatalog.stars();
        assertEquals(83, stars.size());
        Set<String> ids = new HashSet<>();
        for (Star star : stars) {
            assertTrue("duplicate id " + star.id, ids.add(star.id));
            assertTrue(star.raHours >= 0 && star.raHours < 24);
            assertTrue(star.decDegrees >= -90 && star.decDegrees <= 90);
            assertTrue(Double.isFinite(star.magnitude));
        }
        for (ConstellationLine line : StarCatalog.lines()) {
            assertTrue("missing line start " + line.fromId, ids.contains(line.fromId));
            assertTrue("missing line end " + line.toId, ids.contains(line.toId));
            assertFalse(line.fromId.equals(line.toId));
        }
        assertEquals(31, StarCatalog.lines().size());
        assertEquals("polaris", StarCatalog.search("北极星").get(0).id);
        assertEquals("vega", StarCatalog.search("织女星").get(0).id);
        assertEquals("altair", StarCatalog.search("牛郎星").get(0).id);
    }

    public void testProjectionUsesZenithCentreAndCardinalDirections() {
        SkyProjection.Point zenith = SkyProjection.project(90, 123, 200, 100, 0);
        assertTrue(zenith.visible);
        assertEquals(100.0, zenith.x, 1e-9);
        assertEquals(50.0, zenith.y, 1e-9);

        SkyProjection.Point north = SkyProjection.project(0, 0, 200, 100, 0);
        SkyProjection.Point east = SkyProjection.project(0, 90, 200, 100, 0);
        assertEquals(100.0, north.x, 1e-9);
        assertTrue(north.y < 50);
        assertTrue(east.x < 100);
        assertEquals(50.0, east.y, 1e-9);
        assertFalse(SkyProjection.project(-0.01, 0, 200, 100, 0).visible);
    }

    public void testJournalCreateUpdateOrderAndDelete() {
        ObservationStore store = new ObservationStore(context);
        ObservationStore.Entry older = new ObservationStore.Entry(
                "vega", "织女一", "香港", 1000L, 22.3193, 114.1694, "初见");
        ObservationStore.Entry newer = new ObservationStore.Entry(
                "sirius", "天狼星", "北京", 2000L, 39.9042, 116.4074, "明亮");
        store.save(older);
        store.save(newer);

        List<ObservationStore.Entry> entries = store.all();
        assertEquals(2, entries.size());
        assertEquals(newer.id, entries.get(0).id);
        assertEquals("初见", entries.get(1).note);

        older.note = "更新后的笔记";
        store.save(older);
        entries = store.all();
        assertEquals(2, entries.size());
        assertEquals("更新后的笔记", entries.get(1).note);

        store.delete(newer.id);
        entries = store.all();
        assertEquals(1, entries.size());
        assertEquals(older.id, entries.get(0).id);
    }

    public void testMalformedJournalIsReportedAndPreserved() {
        SharedPreferences prefs = context.getSharedPreferences("journal", Context.MODE_PRIVATE);
        String malformed = "{not-json";
        assertTrue(prefs.edit().putString("entries", malformed).commit());
        try {
            new ObservationStore(context).all();
            fail("Malformed journal should fail visibly");
        } catch (IllegalStateException expected) {
            assertEquals(malformed, prefs.getString("entries", null));
        }
    }
}
