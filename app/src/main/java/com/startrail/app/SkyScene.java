package com.startrail.app;

import com.startrail.app.astro.Astronomy;
import com.startrail.app.astro.SolarSystem;
import com.startrail.app.astro.Star;
import com.startrail.app.astro.StarCatalog;
import com.startrail.app.astro.DeepSkyCatalog;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A single consistent observing instant shared by the chart, search and object cards. */
final class SkyScene {
    final Instant time;
    final List<Star> objects;
    final Map<String,Astronomy.HorizontalPosition> positions;
    final Map<String,SolarSystem.BodyPosition> solar;
    private SkyScene(Instant time,List<Star> objects,Map<String,Astronomy.HorizontalPosition> positions,
                     Map<String,SolarSystem.BodyPosition> solar){
        this.time=time;this.objects=Collections.unmodifiableList(objects);
        this.positions=Collections.unmodifiableMap(positions);this.solar=Collections.unmodifiableMap(solar);
    }
    static SkyScene create(Instant time,double latitude,double longitude){
        List<Star> objects=new ArrayList<>(StarCatalog.stars());
        for(DeepSkyCatalog.Entry entry:DeepSkyCatalog.entries())objects.add(entry.star);
        Map<String,Astronomy.HorizontalPosition> positions=new LinkedHashMap<>();
        Map<String,SolarSystem.BodyPosition> solar=new LinkedHashMap<>();
        for(Star star:objects)positions.put(star.id,Astronomy.horizontal(star,time,latitude,longitude));
        for(SolarSystem.BodyPosition body:SolarSystem.snapshot(time,latitude,longitude)){
            solar.put(body.id,body);
            // Dynamic bodies use their topocentric solution below; dummy equatorial fields are never displayed or calculated.
            String type=body.id.equals("sun")?"恒星 · 太阳系":body.id.equals("moon")?"地球的卫星":"太阳系行星";
            String[] aliases=body.id.equals("moon")?new String[]{"月亮","太阴"}:body.id.equals("venus")?new String[]{"启明星","长庚星","太白"}:body.id.equals("sun")?new String[]{"日","太阳系中心"}:new String[0];
            Star target=new Star(body.id,body.englishName,body.name,type,0,0,body.magnitude,body.description,aliases);
            objects.add(target);positions.put(body.id,new Astronomy.HorizontalPosition(body.altitude,body.azimuth));
        }
        objects.sort(Comparator.comparingDouble(s->s.magnitude));
        return new SkyScene(time,objects,positions,solar);
    }
    Star find(String id){for(Star target:objects)if(target.id.equals(id))return target;return null;}
}
