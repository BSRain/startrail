package com.startrail.app;

import android.test.InstrumentationTestCase;

import com.startrail.app.astro.GalacticCoordinates;

public final class GalacticCoordinatesTest extends InstrumentationTestCase {
    public void testGalacticCentreMatchesIcrsReference() {
        GalacticCoordinates.Equatorial p = GalacticCoordinates.galacticToEquatorial(0, 0);
        assertEquals(17.76033, p.raHours, 0.00002);
        assertEquals(-28.93617, p.decDegrees, 0.0002);
    }

    public void testNorthGalacticPoleMatchesDefiningAngles() {
        GalacticCoordinates.Equatorial p = GalacticCoordinates.galacticToEquatorial(0, 90);
        assertEquals(12.8572987, p.raHours, 0.000002);
        assertEquals(27.12825, p.decDegrees, 0.00002);
    }

    public void testAnticentreIsOppositeGalacticCentre() {
        GalacticCoordinates.Equatorial p = GalacticCoordinates.galacticToEquatorial(180, 0);
        assertEquals(5.76033, p.raHours, 0.00002);
        assertEquals(28.93617, p.decDegrees, 0.0002);
    }
}
