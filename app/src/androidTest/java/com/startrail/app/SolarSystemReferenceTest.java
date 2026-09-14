package com.startrail.app;
import com.startrail.app.astro.SolarSystem;
import junit.framework.TestCase;
import java.time.Instant;
import java.util.List;

public final class SolarSystemReferenceTest extends TestCase {
    public void testAgainstTwelveIndependentJplFixtures(){assertTrue(SolarReferenceData.verify()<0.08);}
    public void testNineBodiesAndMoonChangesWithTime(){
        List<SolarSystem.BodyPosition> now=SolarSystem.snapshot(Instant.parse("2026-09-06T12:00:00Z"),39.9042,116.4074);
        List<SolarSystem.BodyPosition> later=SolarSystem.snapshot(Instant.parse("2026-09-13T12:00:00Z"),39.9042,116.4074);
        assertEquals(9,now.size());
        SolarSystem.BodyPosition first=null,second=null;
        for(SolarSystem.BodyPosition p:now){assertTrue(Double.isFinite(p.altitude));assertTrue(p.illumination>=0&&p.illumination<=1);assertTrue(p.distanceKm>0);if(p.id.equals("moon"))first=p;}
        for(SolarSystem.BodyPosition p:later)if(p.id.equals("moon"))second=p;
        assertNotNull(first);assertNotNull(second);
        assertTrue(Math.abs(first.illumination-second.illumination)>0.1);
    }
}
