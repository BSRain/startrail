package com.startrail.app;
import com.startrail.app.astro.SolarSystem;
import java.time.Instant;
/** Fixed NASA/JPL Horizons airless topocentric reference snapshots; see work/solar-tests. */
public final class SolarReferenceData {
    private static final class Fixture {
        String id,time;double lat,lon,alt,az,lit,distance;
        Fixture(String id,String time,double lat,double lon,double alt,double az,double lit,double distance){this.id=id;this.time=time;this.lat=lat;this.lon=lon;this.alt=alt;this.az=az;this.lit=lit;this.distance=distance;}
    }
    private static final Fixture[] CASES={
        new Fixture("sun", "2026-09-06T12:00:00Z", 39.9042000, 116.4074000, -15.8631997280, 292.7612994700, 1.00000000, 150796203.250000),
        new Fixture("moon", "2026-09-06T12:00:00Z", 39.9042000, 116.4074000, -24.3023140830, 357.7327768820, 0.25922930, 370873.105520),
        new Fixture("mercury", "2026-09-06T12:00:00Z", 39.9042000, 116.4074000, -11.2430719220, 284.9188903200, 0.96212800, 205509730.520000),
        new Fixture("venus", "2026-09-06T12:00:00Z", 39.9042000, 116.4074000, -0.3571604160, 251.4593219310, 0.35067480, 76680567.398000),
        new Fixture("mars", "2026-09-06T12:00:00Z", 39.9042000, 116.4074000, -26.8299096710, 353.4389286160, 0.91623790, 272177247.640000),
        new Fixture("jupiter", "2026-09-06T12:00:00Z", 39.9042000, 116.4074000, -24.2885532140, 322.4727583870, 0.99785620, 921096999.620000),
        new Fixture("saturn", "2026-09-06T12:00:00Z", 39.9042000, 116.4074000, 0.7911692000, 86.9963531610, 0.99930640, 1279363443.800000),
        new Fixture("uranus", "2026-09-06T12:00:00Z", 39.9042000, 116.4074000, -19.3629775940, 37.6899009140, 0.99934100, 2883602838.600000),
        new Fixture("neptune", "2026-09-06T12:00:00Z", 39.9042000, 116.4074000, 6.3519521110, 95.2043797640, 0.99996770, 4327476405.100000),
        new Fixture("moon", "2026-09-06T12:00:00Z", -33.8688000, 151.2093000, -61.6423322530, 95.6733213150, 0.25678440, 373873.555010),
        new Fixture("moon", "2024-04-08T18:36:00Z", 30.2672000, -97.7431000, 67.3189350480, 182.2226982670, 0.00000000, 353932.194830),
        new Fixture("sun", "2024-04-08T18:36:00Z", 30.2672000, -97.7431000, 67.3141971320, 182.1843499200, 1.00000000, 149817983.590000)
    };
    public static double verify(){
        double maximum=0;
        for(Fixture f:CASES){
            SolarSystem.BodyPosition actual=null;
            for(SolarSystem.BodyPosition p:SolarSystem.snapshot(Instant.parse(f.time),f.lat,f.lon))if(p.id.equals(f.id))actual=p;
            if(actual==null)throw new AssertionError("Missing body: "+f.id);
            double a=Math.toRadians(actual.altitude),b=Math.toRadians(f.alt),d=Math.toRadians(actual.azimuth-f.az);
            double angle=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,Math.sin(a)*Math.sin(b)+Math.cos(a)*Math.cos(b)*Math.cos(d)))));
            if(angle>0.08)throw new AssertionError(f.id+" JPL position error="+angle);
            if(Math.abs(actual.illumination-f.lit)>0.015)throw new AssertionError(f.id+" phase discrepancy");
            if(Math.abs(actual.distanceKm-f.distance)/f.distance>0.001)throw new AssertionError(f.id+" distance discrepancy");
            maximum=Math.max(maximum,angle);
        }
        return maximum;
    }
}
