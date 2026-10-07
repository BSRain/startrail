package com.startrail.app.astro;

import io.github.cosinekitty.astronomy.*;
import io.github.cosinekitty.astronomy.Observer;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CancellationException;

/** Samples actual event windows at a frozen observer location; never treats missing forecasts as clear. */
public final class EventObservationPlanner {
    private EventObservationPlanner() {}
    public static final class Slot {
        public final Instant start,end,bestTime;
        public final double altitude,azimuth,moonIllumination;
        public final boolean weatherKnown;
        public final String summary;
        public Slot(Instant start,Instant end,Instant bestTime,double altitude,double azimuth,
                    double moonIllumination,boolean weatherKnown,String summary){
            if(start==null||end==null||bestTime==null||!end.isAfter(start)||bestTime.isBefore(start)||bestTime.isAfter(end))
                throw new IllegalArgumentException("slot times");
            this.start=start;this.end=end;this.bestTime=bestTime;this.altitude=altitude;this.azimuth=azimuth;
            this.moonIllumination=moonIllumination;this.weatherKnown=weatherKnown;this.summary=Objects.requireNonNull(summary);
        }
    }
    public static final class ContactVisibility {
        public final String title; public final Instant time; public final double altitude; public final boolean aboveHorizon;
        private ContactVisibility(String title,Instant time,double altitude){this.title=title;this.time=time;this.altitude=altitude;aboveHorizon=altitude>0;}
    }
    public static final class Result {
        public final Slot recommended;
        public final String message;
        public final int visibleSamples,knownWeatherSamples;
        public final List<ContactVisibility> contacts;
        private Result(Slot slot,String message,int visible,int known,List<ContactVisibility> contacts){
            recommended=slot;this.message=message;visibleSamples=visible;knownWeatherSamples=known;this.contacts=contacts;
        }
    }
    private static final class Sample {
        final Instant time;final double altitude,azimuth,moon,score;
        final boolean visible,known,veto;
        Sample(Instant t,double a,double z,double m,double s,boolean v,boolean k,boolean bad){
            time=t;altitude=a;azimuth=z;moon=m;score=s;visible=v;known=k;veto=bad;
        }
        boolean usable(boolean requireWeather){return visible&&!veto&&(!requireWeather||known);}
    }
    public static Result plan(CelestialEvents.Event event,Instant now,double lat,double lon,List<WeatherPlanner.Hour> hours){
        Objects.requireNonNull(event,"event");Objects.requireNonNull(now,"now");Objects.requireNonNull(hours,"hours");
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new IllegalArgumentException("location");
        if(Duration.between(event.start,event.end).isNegative()||Duration.between(event.start,event.end).compareTo(Duration.ofDays(4))>0)
            throw new IllegalArgumentException("event window exceeds four days");
        checkCanceled();
        List<ContactVisibility> contacts=contacts(event,lat,lon);
        if(event.kind==CelestialEvents.Kind.SOLAR_ECLIPSE&&!event.local)
            return new Result(null,"此日食不发生在当前地点；请更换观测地点。",0,0,contacts);
        Instant begin=event.start.isAfter(now)?event.start:now;
        if(!event.end.isAfter(begin))return new Result(null,"此天象的观测窗口已经结束。",0,0,contacts);
        Map<Instant,WeatherPlanner.Hour> weather=new HashMap<>();
        for(WeatherPlanner.Hour h:hours){if(h==null||weather.put(h.time,h)!=null)throw new IllegalArgumentException("duplicate/null weather hour");}
        boolean eclipse=event.kind==CelestialEvents.Kind.SOLAR_ECLIPSE||event.kind==CelestialEvents.Kind.LUNAR_ECLIPSE;
        long step=eclipse?60:300;
        TreeSet<Instant> times=new TreeSet<>();
        for(Instant t=begin;t.isBefore(event.end);t=t.plusSeconds(step))times.add(t);
        times.add(event.end);
        if(!event.peak.isBefore(begin)&&!event.peak.isAfter(event.end))times.add(event.peak);
        for(CelestialEvents.Contact c:event.contacts)if(!c.time.isBefore(begin)&&!c.time.isAfter(event.end))times.add(c.time);
        Observer observer=new Observer(lat,lon,0);
        List<Sample> samples=new ArrayList<>();int visible=0,known=0;
        for(Instant t:times){
            checkCanceled();Sample sample=evaluate(event,t,observer,weather.get(t.truncatedTo(ChronoUnit.HOURS)));
            samples.add(sample);if(sample.visible){visible++;if(sample.known)known++;}
        }
        if(visible==0)return new Result(null,"当前地点在事件窗口内没有满足高度与天色条件的观测时段。",0,0,contacts);
        boolean requireWeather=known>0;
        int best=-1;double score=-Double.MAX_VALUE;
        for(int i=0;i<samples.size();i++){
            Sample s=samples.get(i);if(!s.usable(requireWeather))continue;
            // A lone point is not a viewing interval. Never bridge a vetoed or unobserved sample.
            boolean adjacent=(i>0&&samples.get(i-1).usable(requireWeather)&&samples.get(i-1).known==s.known)
                ||(i+1<samples.size()&&samples.get(i+1).usable(requireWeather)&&samples.get(i+1).known==s.known);
            if(adjacent&&s.score>score){score=s.score;best=i;}
        }
        if(best<0)return new Result(null,known>0?"已知天气不满足观测条件；未知时段不会替代为晴天建议。":"没有连续可用时段；部分天气指标可能不满足条件。",visible,known,contacts);
        Sample b=samples.get(best);int left=best,right=best;
        while(left>0&&samples.get(left-1).usable(requireWeather)&&samples.get(left-1).known==b.known
            &&Duration.between(samples.get(left-1).time,b.time).getSeconds()<=1800)left--;
        while(right+1<samples.size()&&samples.get(right+1).usable(requireWeather)&&samples.get(right+1).known==b.known
            &&Duration.between(b.time,samples.get(right+1).time).getSeconds()<=1800)right++;
        String context=event.kind==CelestialEvents.Kind.METEOR_SHOWER?"流星雨辐射点高于地平线；请观看宽阔天空，不必盯住辐射点。"
            :event.kind==CelestialEvents.Kind.MOON_PHASE&&event.phase==0?"新月附近的暗夜观星窗口，并非观看新月月面。"
            :event.kind==CelestialEvents.Kind.SOLAR_ECLIPSE?"日食观测必须使用合规太阳滤镜，不能直接看太阳。"
            :event.kind==CelestialEvents.Kind.LUNAR_ECLIPSE?"推荐位于食相窗口内；可见阶段请结合下方食相时间与当地高度。"
            :"按天体高度、天色与接近峰值程度排序。";
        String summary=(b.known?"小时天气符合阈值，非可见度保证。":"仅天文建议，天气未知；临近日期请刷新天气。")+context;
        Slot slot=new Slot(samples.get(left).time,samples.get(right).time,b.time,b.altitude,b.azimuth,b.moon,b.known,summary);
        String message=known>0?"推荐只采用有完整云量和降水资料的时段；地形、光污染和雾霾未建模。":"尚无覆盖可观测时段的完整预报，以下仅为天文候选。";
        return new Result(slot,message,visible,known,contacts);
    }
    private static List<ContactVisibility> contacts(CelestialEvents.Event event,double lat,double lon){
        List<ContactVisibility> list=new ArrayList<>();Observer observer=new Observer(lat,lon,0);
        Body body=event.kind==CelestialEvents.Kind.SOLAR_ECLIPSE?Body.Sun:Body.Moon;
        for(CelestialEvents.Contact c:event.contacts){
            checkCanceled();Time t=Time.fromMillisecondsSince1970(c.time.toEpochMilli());
            list.add(new ContactVisibility(c.title,c.time,position(body,t,observer).getAltitude()));
        }
        return Collections.unmodifiableList(list);
    }
    private static Sample evaluate(CelestialEvents.Event e,Instant instant,Observer observer,WeatherPlanner.Hour weather){
        Time time=new Time(instant.getEpochSecond()/86400.0+instant.getNano()/86400e9-10957.5);
        Topocentric sun=position(Body.Sun,time,observer),moon=position(Body.Moon,time,observer);
        double fraction=io.github.cosinekitty.astronomy.Astronomy.illumination(Body.Moon,time).getPhaseFraction();
        double altitude,azimuth;boolean visible;
        if(e.kind==CelestialEvents.Kind.SOLAR_ECLIPSE){altitude=sun.getAltitude();azimuth=sun.getAzimuth();visible=altitude>0;}
        else if(e.kind==CelestialEvents.Kind.MOON_PHASE&&e.phase==0){altitude=Double.NaN;azimuth=Double.NaN;visible=sun.getAltitude()<=-18;}
        else if(e.kind==CelestialEvents.Kind.METEOR_SHOWER){
            Astronomy.HorizontalPosition radiant=Astronomy.horizontal(e.radiantRaHours,e.radiantDecDegrees,instant,observer.getLatitude(),observer.getLongitude());
            altitude=radiant.altitude;azimuth=radiant.azimuth;visible=altitude>=20&&sun.getAltitude()<=-18;
        }else{
            Body primary=body(e.primaryId);Topocentric target=primary==Body.Moon?moon:position(primary,time,observer);
            altitude=target.getAltitude();azimuth=target.getAzimuth();
            if(e.kind==CelestialEvents.Kind.LUNAR_ECLIPSE)visible=altitude>0&&sun.getAltitude()<=0;
            else visible=altitude>=10&&sun.getAltitude()<=-6;
            if(e.kind==CelestialEvents.Kind.CLOSE_APPROACH&&!e.secondaryId.isEmpty()){
                Topocentric second=position(body(e.secondaryId),time,observer);
                double a=Math.toRadians(altitude),b=Math.toRadians(second.getAltitude());
                double delta=Math.toRadians(azimuth-second.getAzimuth());
                double cosine=Math.sin(a)*Math.sin(b)+Math.cos(a)*Math.cos(b)*Math.cos(delta);
                double separation=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,cosine))));
                visible&=second.getAltitude()>=10&&separation<=(e.primaryId.equals("moon")?8:5);
            }
        }
        boolean known=weather!=null&&weather.hasObservingData();
        boolean veto=weather!=null&&((Double.isFinite(weather.cloudPercent)&&weather.cloudPercent>=80)
            ||(Double.isFinite(weather.precipMm)&&weather.precipMm>.1)||(Double.isFinite(weather.windKmh)&&weather.windKmh>=40));
        double score=Double.isFinite(altitude)?altitude:45;
        double delta=Math.abs(Duration.between(e.peak,instant).toMillis()/3600000.0);
        score-=delta*(e.kind==CelestialEvents.Kind.SOLAR_ECLIPSE||e.kind==CelestialEvents.Kind.LUNAR_ECLIPSE?120:2);
        if(e.kind==CelestialEvents.Kind.METEOR_SHOWER&&moon.getAltitude()>0)score-=fraction*45;
        if(known){score-=weather.cloudPercent*.4;if(Double.isFinite(weather.windKmh))score-=weather.windKmh*.15;
            if(Double.isFinite(weather.humidity))score-=Math.max(0,weather.humidity-70)*.15;}
        return new Sample(instant,altitude,azimuth,fraction,score,visible,known,veto);
    }
    private static Topocentric position(Body body,Time time,Observer observer){
        Equatorial eq=io.github.cosinekitty.astronomy.Astronomy.equator(body,time,observer,EquatorEpoch.OfDate,Aberration.Corrected);
        return io.github.cosinekitty.astronomy.Astronomy.horizon(time,observer,eq.getRa(),eq.getDec(),Refraction.None);
    }
    private static Body body(String id){
        switch(id){case "sun":return Body.Sun;case "moon":return Body.Moon;case "mercury":return Body.Mercury;case "venus":return Body.Venus;
            case "mars":return Body.Mars;case "jupiter":return Body.Jupiter;case "saturn":return Body.Saturn;case "uranus":return Body.Uranus;case "neptune":return Body.Neptune;
            default:throw new IllegalArgumentException("unknown body");}
    }
    private static void checkCanceled(){if(Thread.currentThread().isInterrupted())throw new CancellationException("event planning canceled");}
}
