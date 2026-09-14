package com.startrail.app.astro;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

/**
 * Bounded, offline observing suggestions combined with caller-supplied hourly weather.
 * All times, including {@link Hour#time}, are UTC {@link Instant}s. Forecast freshness is
 * deliberately a caller concern. A returned pick is a ranked candidate, never a visibility
 * guarantee; terrain, haze, light pollution and instrument limits are outside this model.
 */
public final class WeatherPlanner {
    public static final int DEFAULT_SAMPLE_MINUTES = 20;
    public static final int MIN_SAMPLE_MINUTES = 10;
    public static final int MAX_SAMPLE_MINUTES = 30;
    private static final int MAX_PICKS = 5;

    private WeatherPlanner() {}

    /**
     * Immutable hourly forecast. Use {@link Double#NaN} for a missing measurement. The timestamp
     * must be aligned to a UTC hour; its values apply until the next UTC hour.
     */
    public static final class Hour {
        public final Instant time;
        public final double cloudPercent, precipMm, humidity, windKmh;

        public Hour(Instant time, double cloudPercent, double precipMm,
                    double humidity, double windKmh) {
            if (time == null || !time.equals(time.truncatedTo(ChronoUnit.HOURS)))
                throw new IllegalArgumentException("weather time must be a UTC whole hour");
            validateMeasure("cloudPercent", cloudPercent, 0, 100);
            validateMeasure("precipMm", precipMm, 0, Double.MAX_VALUE);
            validateMeasure("humidity", humidity, 0, 100);
            validateMeasure("windKmh", windKmh, 0, Double.MAX_VALUE);
            this.time = time;
            this.cloudPercent = cloudPercent;
            this.precipMm = precipMm;
            this.humidity = humidity;
            this.windKmh = windKmh;
        }

        /** Cloud and precipitation are the minimum facts needed to call an hour usable. */
        public boolean hasObservingData() {
            return Double.isFinite(cloudPercent) && Double.isFinite(precipMm);
        }

        private static void validateMeasure(String name, double value, double min, double max) {
            if (!Double.isNaN(value) && (!Double.isFinite(value) || value < min || value > max))
                throw new IllegalArgumentException(name);
        }
    }

    public static Result plan(Instant start, Instant end, double latitude, double longitude,
                              List<Hour> weatherHours) {
        return plan(start, end, latitude, longitude, weatherHours, DEFAULT_SAMPLE_MINUTES);
    }

    /**
     * Samples the inclusive user window. Its duration must be positive and at most 24 hours.
     * Weather matches by the sample's UTC hour; gaps and missing essential values remain unknown.
     *
     * @throws CancellationException if the worker thread is interrupted
     */
    public static Result plan(Instant start, Instant end, double latitude, double longitude,
                              List<Hour> weatherHours, int sampleMinutes) {
        validate(start, end, latitude, longitude, weatherHours, sampleMinutes);
        Map<Instant, Hour> weather = indexWeather(weatherHours);
        Map<String, Candidate> best = new LinkedHashMap<>();
        Map<String, Candidate> unknownBest = new LinkedHashMap<>();
        int total = 0, known = 0, acceptable = 0;
        boolean hasDark = false;

        for (Instant time = start; !time.isAfter(end); time = time.plus(Duration.ofMinutes(sampleMinutes))) {
            if (Thread.currentThread().isInterrupted())
                throw new CancellationException("Weather planning was cancelled");
            Hour hour = weather.get(time.truncatedTo(ChronoUnit.HOURS));
            boolean weatherKnown = hour != null && hour.hasObservingData();
            List<SolarSystem.BodyPosition> bodies = SolarSystem.snapshot(time, latitude, longitude);
            SolarSystem.BodyPosition sun = body(bodies, "sun");
            if (sun == null || sun.altitude > -6.0) continue;
            hasDark = true;
            total++;
            if (weatherKnown) known++;
            boolean weatherVeto = hour != null
                    && ((Double.isFinite(hour.cloudPercent) && hour.cloudPercent >= 80.0)
                    || (Double.isFinite(hour.precipMm) && hour.precipMm > 0.1)
                    || (Double.isFinite(hour.windKmh) && hour.windKmh >= 40.0));
            if (weatherVeto) continue;
            boolean unknownWeather = !weatherKnown;
            if (!unknownWeather) acceptable++;
            SolarSystem.BodyPosition moon = body(bodies, "moon");

            for (SolarSystem.BodyPosition target : bodies) {
                if ("sun".equals(target.id) || !("moon".equals(target.kind) || "planet".equals(target.kind))
                        || target.altitude < 20.0) continue;
                consider(unknownWeather ? unknownBest : best,
                        candidate(target.id, target.displayName(), target.kind, time,
                                target.altitude, target.azimuth, target.magnitude, null, hour, 0.0));
            }
            for (Star star : StarCatalog.stars()) {
                if (star.magnitude > 2.0) continue;
                Astronomy.HorizontalPosition p = Astronomy.horizontal(star, time, latitude, longitude);
                if (p.altitudeDegrees >= 20.0)
                    consider(unknownWeather ? unknownBest : best,
                            candidate(star.id, star.displayName(), "star", time,
                                    p.altitudeDegrees, p.azimuthDegrees, star.magnitude, null, hour, 0.0));
            }
            if (sun.altitude <= -18.0) {
                for (DeepSkyCatalog.Entry entry : DeepSkyCatalog.entries()) {
                    Astronomy.HorizontalPosition p = Astronomy.horizontal(entry.star, time, latitude, longitude);
                    if (p.altitudeDegrees < 20.0) continue;
                    double moonPenalty = moonPenalty(moon, p.altitudeDegrees, p.azimuthDegrees);
                    consider(unknownWeather ? unknownBest : best,
                            candidate(entry.star.id, entry.star.displayName(), "deep_sky", time,
                                    p.altitudeDegrees, p.azimuthDegrees, entry.star.magnitude,
                                    entry.equipment, hour, moonPenalty));
                }
            }
        }

        // When the caller supplied no usable weather at all, retain useful astronomy-only output,
        // explicitly labelled as unknown rather than silently calling those conditions good.
        if (known == 0) best.putAll(unknownBest);
        List<Candidate> ranked = new ArrayList<>(best.values());
        ranked.sort(Comparator.comparingDouble((Candidate c) -> c.score).reversed()
                .thenComparing(c -> c.id));
        if (ranked.size() > MAX_PICKS) ranked = ranked.subList(0, MAX_PICKS);
        List<Pick> picks = new ArrayList<>();
        for (Candidate c : ranked) picks.add(c.toPick());
        picks.sort(Comparator.comparing((Pick p) -> p.bestTime).thenComparing(p -> p.targetId));

        String message;
        if (!hasDark) message = "所选时段内没有太阳低于 -6° 的观测时刻。";
        else if (known == 0) message = "天气资料缺失，无法把任何时刻标记为良好观测条件。";
        else if (acceptable == 0) message = "已知天气的降水、云量或风速不满足本计划阈值，没有推荐时刻。";
        else if (picks.isEmpty()) message = "存在暗夜和可用天气，但没有目录目标达到最低高度。";
        else if (known < total) message = "部分小时天气未知；推荐仅来自资料完整且条件可用的时刻。";
        else message = "推荐基于天体高度、暗夜和逐小时天气，实际可见度仍受现场环境影响。";
        return new Result(start, end, picks, hasDark, total, known, acceptable, sampleMinutes, message);
    }

    private static Candidate candidate(String id, String name, String kind, Instant time,
                                       double altitude, double azimuth, double magnitude,
                                       String equipment, Hour weather, double moonPenalty) {
        double weatherPenalty;
        if (weather == null || !weather.hasObservingData()) weatherPenalty = 20.0;
        else {
            weatherPenalty = weather.cloudPercent * 0.32 + weather.precipMm * 20.0;
            if (Double.isFinite(weather.humidity)) weatherPenalty += Math.max(0, weather.humidity - 70) * 0.12;
            if (Double.isFinite(weather.windKmh)) weatherPenalty += Math.max(0, weather.windKmh - 15) * 0.15;
        }
        double brightnessPenalty = "deep_sky".equals(kind) ? 0 : Math.max(0, magnitude + 1) * 1.5;
        return new Candidate(id, name, kind, time, altitude, azimuth,
                altitude - weatherPenalty - brightnessPenalty - moonPenalty,
                equipment, weather, moonPenalty);
    }

    private static double moonPenalty(SolarSystem.BodyPosition moon, double altitude, double azimuth) {
        if (moon == null || moon.altitude <= 0 || moon.illumination <= 0) return 0;
        double a1 = Math.toRadians(altitude), a2 = Math.toRadians(moon.altitude);
        double daz = Math.toRadians(azimuth - moon.azimuth);
        double separation = Math.toDegrees(Math.acos(clamp(Math.sin(a1) * Math.sin(a2)
                + Math.cos(a1) * Math.cos(a2) * Math.cos(daz), -1, 1)));
        return moon.illumination * (8.0 + Math.max(0, 60.0 - separation) * 0.35);
    }

    private static void consider(Map<String, Candidate> best, Candidate next) {
        Candidate old = best.get(next.id);
        if (old == null || next.score > old.score
                || (next.score == old.score && next.time.isBefore(old.time))) best.put(next.id, next);
    }

    private static SolarSystem.BodyPosition body(List<SolarSystem.BodyPosition> bodies, String id) {
        for (SolarSystem.BodyPosition body : bodies) if (id.equals(body.id)) return body;
        return null;
    }

    private static Map<Instant, Hour> indexWeather(List<Hour> hours) {
        Map<Instant, Hour> result = new HashMap<>();
        for (Hour hour : hours) {
            if (hour == null) throw new IllegalArgumentException("weatherHours contains null");
            if (result.put(hour.time, hour) != null)
                throw new IllegalArgumentException("duplicate weather hour: " + hour.time);
        }
        return result;
    }

    private static void validate(Instant start, Instant end, double latitude, double longitude,
                                 List<Hour> weatherHours, int sampleMinutes) {
        if (start == null || end == null) throw new IllegalArgumentException("start/end");
        Instant minimum = Instant.ofEpochMilli(TimeAxis.MIN_MILLIS);
        Instant maximum = Instant.ofEpochMilli(TimeAxis.MAX_MILLIS);
        if (start.isBefore(minimum) || end.isAfter(maximum))
            throw new IllegalArgumentException("window outside supported time range");
        Duration duration = Duration.between(start, end);
        if (duration.isZero() || duration.isNegative() || duration.compareTo(Duration.ofHours(24)) > 0)
            throw new IllegalArgumentException("window must be > 0 and <= 24 hours");
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90)
            throw new IllegalArgumentException("latitude");
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180)
            throw new IllegalArgumentException("longitude");
        if (weatherHours == null) throw new IllegalArgumentException("weatherHours");
        if (sampleMinutes < MIN_SAMPLE_MINUTES || sampleMinutes > MAX_SAMPLE_MINUTES)
            throw new IllegalArgumentException("sampleMinutes must be in [10,30]");
    }

    private static double clamp(double x, double low, double high) {
        return Math.max(low, Math.min(high, x));
    }

    private static final class Candidate {
        final String id, name, kind, equipment;
        final Instant time;
        final double altitude, azimuth, score, moonPenalty;
        final Hour weather;
        Candidate(String id, String name, String kind, Instant time, double altitude,
                  double azimuth, double score, String equipment, Hour weather, double moonPenalty) {
            this.id=id; this.name=name; this.kind=kind; this.time=time; this.altitude=altitude;
            this.azimuth=azimuth; this.score=score; this.equipment=equipment;
            this.weather=weather; this.moonPenalty=moonPenalty;
        }
        Pick toPick() {
            String reason = weather == null || !weather.hasObservingData()
                    ? "仅天文候选，天气未知；不代表实际可见。"
                    : "deep_sky".equals(kind)
                    ? "天文暮光结束且目标高度合适；月光已计入排序，仍不代表现场可见。"
                    : "目标高度合适，且该小时云量和降水条件可用。";
            String gear = equipment != null ? equipment : "moon".equals(kind)
                    ? "肉眼或双筒望远镜" : "planet".equals(kind)
                    ? (("neptune".equals(id) || "uranus".equals(id)) ? "双筒或天文望远镜；海王星需望远镜" : "肉眼；小型望远镜可看更多细节") : "肉眼";
            return new Pick(id, name, kind, time, altitude, azimuth, score, reason, gear,
                    weather, moonPenalty);
        }
    }

    public static final class Result {
        public final Instant start, end;
        public final List<Pick> picks;
        public final boolean hasDarkWindow;
        /** Counts below cover samples where the Sun was at or below -6 degrees. */
        public final int totalSamples, knownWeatherSamples, acceptableWeatherSamples, sampleMinutes;
        public final String message;
        private Result(Instant start, Instant end, List<Pick> picks, boolean hasDarkWindow,
                       int totalSamples, int knownWeatherSamples, int acceptableWeatherSamples,
                       int sampleMinutes, String message) {
            this.start=start; this.end=end; this.picks=Collections.unmodifiableList(new ArrayList<>(picks));
            this.hasDarkWindow=hasDarkWindow; this.totalSamples=totalSamples;
            this.knownWeatherSamples=knownWeatherSamples;
            this.acceptableWeatherSamples=acceptableWeatherSamples;
            this.sampleMinutes=sampleMinutes; this.message=message;
        }
    }

    public static final class Pick {
        public final String targetId, displayName, kind;
        public final Instant bestTime;
        public final double altitudeDegrees, azimuthDegrees, score, moonInterferencePenalty;
        public final String reason, equipment;
        /** Exact immutable hourly weather object that qualified this pick. */
        public final Hour weather;
        private Pick(String targetId, String displayName, String kind, Instant bestTime,
                     double altitudeDegrees, double azimuthDegrees, double score, String reason,
                     String equipment, Hour weather, double moonInterferencePenalty) {
            this.targetId=targetId; this.displayName=displayName; this.kind=kind; this.bestTime=bestTime;
            this.altitudeDegrees=altitudeDegrees; this.azimuthDegrees=azimuthDegrees; this.score=score;
            this.reason=reason; this.equipment=equipment; this.weather=weather;
            this.moonInterferencePenalty=moonInterferencePenalty;
        }
    }
}
