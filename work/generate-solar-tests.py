from pathlib import Path
import json
root=Path(__file__).resolve().parent.parent
rows=json.loads((root/'work/solar-tests/horizons-fixtures.json').read_text())
data=',\n'.join('        new Fixture("%s", "%s", %.7f, %.7f, %.10f, %.10f, %.8f, %.6f)'%(r['id'],r['time'],r['latitude'],r['longitude'],r['altitude'],r['azimuth'],r['illumination'],r['distanceKm']) for r in rows)
source='''package com.startrail.app;
import com.startrail.app.astro.SolarSystem;
import java.time.Instant;
/** Fixed NASA/JPL Horizons airless topocentric reference snapshots; see work/solar-tests. */
public final class SolarReferenceData {
    private static final class Fixture {
        String id,time;double lat,lon,alt,az,lit,distance;
        Fixture(String id,String time,double lat,double lon,double alt,double az,double lit,double distance){this.id=id;this.time=time;this.lat=lat;this.lon=lon;this.alt=alt;this.az=az;this.lit=lit;this.distance=distance;}
    }
    private static final Fixture[] CASES={
'''+data+'''
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
'''
(root/'app/src/androidTest/java/com/startrail/app/SolarReferenceData.java').write_text(source,encoding='utf8')
(root/'work/solar-tests/SolarVerification.java').write_text('''import com.startrail.app.SolarReferenceData;
public class SolarVerification { public static void main(String[] args){ System.out.println("PASS: 12 NASA/JPL fixtures; max angular error="+SolarReferenceData.verify()+" degrees"); } }
''',encoding='utf8')
