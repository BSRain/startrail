package com.startrail.app.astro;

import org.junit.Test;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public final class EventObservationPlannerTest {
    private static final Instant NIGHT=Instant.parse("2026-09-11T12:00:00Z");
    private CelestialEvents.Event dark(Instant start,Instant end){
        return event(CelestialEvents.Kind.MOON_PHASE,start,end,"moon","",0,true);
    }
    private CelestialEvents.Event event(CelestialEvents.Kind kind,Instant start,Instant end,String a,String b,int phase,boolean local){
        return new CelestialEvents.Event("test","test","test","https://example.org",kind,
            start.plusSeconds(Duration.between(start,end).getSeconds()/2),start,end,a,b,false,local,phase,Double.NaN,Double.NaN,Double.NaN,Collections.emptyList());
    }
    @Test public void unknownForecastProducesExplicitAstronomyOnlyWindow(){
        EventObservationPlanner.Result r=EventObservationPlanner.plan(dark(NIGHT,NIGHT.plusSeconds(10800)),NIGHT,22.3,114.2,Collections.emptyList());
        assertNotNull(r.recommended);assertFalse(r.recommended.weatherKnown);assertTrue(r.recommended.summary.contains("天气未知"));
        assertFalse(r.recommended.start.isBefore(NIGHT));assertTrue(r.recommended.end.isAfter(r.recommended.start));
    }
    @Test public void clearForecastIsMatchedToUtcHours(){
        List<WeatherPlanner.Hour> hours=new ArrayList<>();
        for(int i=0;i<4;i++)hours.add(new WeatherPlanner.Hour(NIGHT.plusSeconds(i*3600),10,0,50,5));
        EventObservationPlanner.Result r=EventObservationPlanner.plan(dark(NIGHT,NIGHT.plusSeconds(10800)),NIGHT,22.3,114.2,hours);
        assertNotNull(r.recommended);assertTrue(r.recommended.weatherKnown);assertTrue(r.knownWeatherSamples>0);
    }
    @Test public void knownRainNeverFallsBackToAnUnknownHour(){
        List<WeatherPlanner.Hour> hours=Collections.singletonList(new WeatherPlanner.Hour(NIGHT,0,2,50,0));
        EventObservationPlanner.Result r=EventObservationPlanner.plan(dark(NIGHT,NIGHT.plusSeconds(7200)),NIGHT,22.3,114.2,hours);
        assertNull(r.recommended);assertTrue(r.visibleSamples>0);assertTrue(r.knownWeatherSamples>0);
    }
    @Test public void partialDataWithKnownSevereWindIsStillVetoed(){
        List<WeatherPlanner.Hour> hours=new ArrayList<>();
        for(int i=0;i<4;i++)hours.add(new WeatherPlanner.Hour(NIGHT.plusSeconds(i*3600),Double.NaN,Double.NaN,Double.NaN,45));
        assertNull(EventObservationPlanner.plan(dark(NIGHT,NIGHT.plusSeconds(10800)),NIGHT,22.3,114.2,hours).recommended);
    }
    @Test public void daytimeNewMoonHasNoDarkSkyRecommendation(){
        Instant noon=Instant.parse("2026-09-11T04:00:00Z");
        assertNull(EventObservationPlanner.plan(dark(noon,noon.plusSeconds(1800)),noon,22.3,114.2,Collections.emptyList()).recommended);
    }
    @Test public void solarEclipseMayBeRecommendedInDaylightOnlyWithinContacts(){
        Instant begin=Instant.parse("2024-04-08T17:30:00Z"),end=Instant.parse("2024-04-08T19:30:00Z");
        CelestialEvents.Event e=event(CelestialEvents.Kind.SOLAR_ECLIPSE,begin,end,"sun","",-1,true);
        EventObservationPlanner.Result r=EventObservationPlanner.plan(e,begin,32.78,-96.8,Collections.emptyList());
        assertNotNull(r.recommended);assertTrue(r.recommended.altitude>0);assertFalse(r.recommended.end.isAfter(end));
    }
    @Test public void lunarEclipseDoesNotRequireSunAboveHorizon(){
        Instant begin=Instant.parse("2025-09-07T17:00:00Z"),end=Instant.parse("2025-09-07T19:00:00Z");
        CelestialEvents.Event e=event(CelestialEvents.Kind.LUNAR_ECLIPSE,begin,end,"moon","sun",-1,false);
        assertNotNull(EventObservationPlanner.plan(e,begin,22.3,114.2,Collections.emptyList()).recommended);
    }
    @Test public void absentLocalEclipseAndPastEventCannotBeBooked(){
        CelestialEvents.Event e=event(CelestialEvents.Kind.SOLAR_ECLIPSE,NIGHT,NIGHT.plusSeconds(3600),"sun","",-1,false);
        assertNull(EventObservationPlanner.plan(e,NIGHT,22.3,114.2,Collections.emptyList()).recommended);
        assertNull(EventObservationPlanner.plan(dark(NIGHT,NIGHT.plusSeconds(3600)),NIGHT.plusSeconds(7200),22.3,114.2,Collections.emptyList()).recommended);
    }
    @Test public void changingLongitudeChangesEligibleWindow(){
        CelestialEvents.Event e=dark(NIGHT,NIGHT.plusSeconds(1800));
        assertNotNull(EventObservationPlanner.plan(e,NIGHT,22.3,114.2,Collections.emptyList()).recommended);
        assertNull(EventObservationPlanner.plan(e,NIGHT,22.3,-65.8,Collections.emptyList()).recommended);
    }
    @Test public void eclipseContactsExplainLocalHorizonAndAreImmutable(){
        Instant begin=Instant.parse("2025-09-06T00:00:00Z");
        CelestialEvents.Event eclipse=CelestialEvents.search(begin,begin.plusSeconds(3*86400),22.3,114.2).stream()
            .filter(e->e.kind==CelestialEvents.Kind.LUNAR_ECLIPSE).findFirst().get();
        EventObservationPlanner.Result r=EventObservationPlanner.plan(eclipse,begin,22.3,114.2,Collections.emptyList());
        assertEquals(7,r.contacts.size());assertNotNull(r.recommended);
        assertTrue(r.contacts.stream().anyMatch(c->c.title.equals("食甚")&&c.aboveHorizon));
        try{r.contacts.clear();fail("immutable");}catch(UnsupportedOperationException expected){}
    }
    @Test public void closeApproachRecommendationKeepsBothBodiesNear(){
        Instant begin=Instant.parse("2025-01-01T00:00:00Z");int observed=0;
        for(CelestialEvents.Event e:CelestialEvents.search(begin,begin.plusSeconds(365*86400),22.3,114.2)){
            if(e.kind!=CelestialEvents.Kind.CLOSE_APPROACH)continue;
            EventObservationPlanner.Result r=EventObservationPlanner.plan(e,begin,22.3,114.2,Collections.emptyList());
            if(r.recommended==null)continue;observed++;
            List<SolarSystem.BodyPosition> bodies=SolarSystem.snapshot(r.recommended.bestTime,22.3,114.2);
            SolarSystem.BodyPosition a=bodies.stream().filter(b->b.id.equals(e.primaryId)).findFirst().get();
            SolarSystem.BodyPosition b=bodies.stream().filter(v->v.id.equals(e.secondaryId)).findFirst().get();
            assertTrue(a.altitude>=10&&b.altitude>=10);
            double aa=Math.toRadians(a.altitude),ba=Math.toRadians(b.altitude),delta=Math.toRadians(a.azimuth-b.azimuth);
            double separation=Math.toDegrees(Math.acos(Math.min(1,Math.sin(aa)*Math.sin(ba)+Math.cos(aa)*Math.cos(ba)*Math.cos(delta))));
            assertTrue("recommended time must retain a close view",separation<=(e.primaryId.equals("moon")?8:5)+1e-6);
        }
        assertTrue("must exercise an observable pair",observed>0);
    }
    @Test(expected=IllegalArgumentException.class) public void invalidLocationRejected(){
        EventObservationPlanner.plan(dark(NIGHT,NIGHT.plusSeconds(3600)),NIGHT,91,0,Collections.emptyList());
    }
    @Test(expected=java.util.concurrent.CancellationException.class) public void interruptionStopsPlanning(){
        Thread.currentThread().interrupt();try{EventObservationPlanner.plan(dark(NIGHT,NIGHT.plusSeconds(3600)),NIGHT,22.3,114.2,Collections.emptyList());}finally{Thread.interrupted();}
    }
}
