package com.startrail.app;

import com.startrail.app.astro.DeepSkyCatalog;
import com.startrail.app.astro.Star;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import junit.framework.TestCase;

public final class DeepSkyCatalogTest extends TestCase {
    public void testCuratedEntriesAreValidAndHaveStableIds() {
        List<DeepSkyCatalog.Entry> entries = DeepSkyCatalog.entries();
        assertEquals(14, entries.size());
        Set<String> ids = new HashSet<>();
        Set<String> kinds = new HashSet<>();
        for (DeepSkyCatalog.Entry entry : entries) {
            Star star = entry.star;
            assertTrue(ids.add(star.id));
            assertTrue(star.raHours >= 0 && star.raHours < 24);
            assertTrue(star.decDeg >= -90 && star.decDeg <= 90);
            assertTrue(Double.isFinite(star.magnitude));
            assertTrue(entry.angularSizeArcmin > 0);
            assertFalse(entry.label.isEmpty());
            assertFalse(entry.equipment.isEmpty());
            kinds.add(entry.kind);
        }
        assertTrue(kinds.contains(DeepSkyCatalog.GALAXY));
        assertTrue(kinds.contains(DeepSkyCatalog.OPEN_CLUSTER));
        assertTrue(kinds.contains(DeepSkyCatalog.GLOBULAR_CLUSTER));
        assertTrue(kinds.contains(DeepSkyCatalog.NEBULA));
        assertTrue(kinds.contains(DeepSkyCatalog.PLANETARY_NEBULA));
    }

    public void testLookupIgnoresCaseAndWhitespaceAndUsesAliases() {
        assertEquals("m31", DeepSkyCatalog.find(" M 31 ").star.id);
        assertEquals("m31", DeepSkyCatalog.find("ngc 224").star.id);
        assertEquals("omega_cen", DeepSkyCatalog.find("OMEGA CENTAURI").star.id);
        assertNull(DeepSkyCatalog.find("not-an-object"));
        assertNull(DeepSkyCatalog.find(null));
    }

    public void testEntriesAreUnmodifiable() {
        try {
            DeepSkyCatalog.entries().clear();
            fail("Expected immutable catalogue");
        } catch (UnsupportedOperationException expected) { /* expected */ }
    }

    public void testReferenceCoordinateFixtures() {
        assertEquals(0.7123, DeepSkyCatalog.find("m31").star.raHours, 0.0001);
        assertEquals(41.2691, DeepSkyCatalog.find("m31").star.decDeg, 0.0001);
        assertEquals(5.5879, DeepSkyCatalog.find("m42").star.raHours, 0.0001);
        assertEquals(-47.4769, DeepSkyCatalog.find("omega_cen").star.decDeg, 0.0001);
    }
}
