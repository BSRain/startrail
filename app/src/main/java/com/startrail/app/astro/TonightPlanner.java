package com.startrail.app.astro;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

/** Offline, beginner-friendly suggestions for the next observable part of a night. */
public final class TonightPlanner {
    public static final int SAMPLE_MINUTES = 20;
    private static final double MAX_SUN_ALTITUDE = -6.0;
    private static final double MAX_DEEP_SKY_SUN_ALTITUDE = -18.0;
    private static final double MIN_TARGET_ALTITUDE = 20.0;
    private static final int MAX_PICKS = 5;

    private TonightPlanner() {}

    /**
     * Samples the selected location from {@code selectedTime} through the following 24 hours.
     * If it is already night, only the remaining current night is used; otherwise the next night
     * is used. Times are Instants, so this calculation does not infer a city or time zone.
     *
     * @throws CancellationException if the worker thread is interrupted
     */
    public static Result plan(Instant selectedTime, double latitude, double longitude) {
        validate(selectedTime, latitude, longitude);
        Instant requestedLimit = selectedTime.plus(Duration.ofHours(24));
        Instant maximum = Instant.ofEpochMilli(TimeAxis.MAX_MILLIS);
        Instant limit = requestedLimit.isAfter(maximum) ? maximum : requestedLimit;
        Instant firstDark = null, lastDark = null;
        boolean enteredDark = false;
        Map<String, Candidate> best = new HashMap<>();
        Map<String, Segment> active = new HashMap<>();

        for (Instant time = selectedTime; !time.isAfter(limit);
             time = time.plus(Duration.ofMinutes(SAMPLE_MINUTES))) {
            if (Thread.currentThread().isInterrupted())
                throw new CancellationException("Tonight planning was cancelled");
            List<SolarSystem.BodyPosition> bodies = SolarSystem.snapshot(time, latitude, longitude);
            SolarSystem.BodyPosition sun = findBody(bodies, "sun");
            boolean dark = sun != null && sun.altitude <= MAX_SUN_ALTITUDE;
            if (!dark) {
                if (enteredDark) break;
                continue;
            }
            enteredDark = true;
            if (firstDark == null) firstDark = time;
            lastDark = time;

            Map<String, Candidate> visible = new HashMap<>();
            for (SolarSystem.BodyPosition body : bodies) {
                if (!isSupportedBody(body.id) || body.altitude < MIN_TARGET_ALTITUDE) continue;
                visible.put(body.id, new Candidate(body.id, body.displayName(), body.kind, time,
                        body.altitude, body.azimuth, body.magnitude, null, null));
            }
            for (Star star : StarCatalog.stars()) {
                if (star.magnitude > 1.5) continue;
                Astronomy.HorizontalPosition hp = Astronomy.horizontal(star, time, latitude, longitude);
                if (hp.altitudeDegrees < MIN_TARGET_ALTITUDE) continue;
                visible.put(star.id, new Candidate(star.id, star.displayName(), "star", time,
                        hp.altitudeDegrees, hp.azimuthDegrees, star.magnitude, null, null));
            }
            if (sun.altitude <= MAX_DEEP_SKY_SUN_ALTITUDE) {
                for (DeepSkyCatalog.Entry entry : DeepSkyCatalog.entries()) {
                    Star target = entry.star;
                    Astronomy.HorizontalPosition hp = Astronomy.horizontal(
                            target, time, latitude, longitude);
                    if (hp.altitudeDegrees < MIN_TARGET_ALTITUDE) continue;
                    visible.put(target.id, new Candidate(target.id, target.displayName(), "deep_sky",
                            time, hp.altitudeDegrees, hp.azimuthDegrees, target.magnitude,
                            null, entry.equipment));
                }
            }
            for (String id : new ArrayList<>(active.keySet())) {
                if (!visible.containsKey(id)) active.remove(id);
            }
            for (Candidate observation : visible.values()) {
                Segment segment = active.get(observation.id);
                if (segment == null) {
                    segment = new Segment(time);
                    active.put(observation.id, segment);
                }
                segment.end = time;
                consider(best, new Candidate(observation.id, observation.name, observation.kind,
                        observation.time, observation.altitude, observation.azimuth,
                        observation.magnitude, segment, observation.equipment));
            }
        }

        if (firstDark == null) {
            return new Result(null, null, Collections.emptyList(), false,
                    "未来24小时内没有太阳低于-6°的可用观测时段。", SAMPLE_MINUTES);
        }

        List<Candidate> ranked = new ArrayList<>(best.values());
        ranked.sort(Comparator.comparingDouble(Candidate::score).reversed()
                .thenComparing(c -> c.id));
        List<Candidate> selected = new ArrayList<>();
        for (Candidate c : ranked) {
            if ((c.kind.equals("moon") || c.kind.equals("planet")) && selected.size() < 2)
                selected.add(c);
        }
        // Integrated deep-sky magnitude is not comparable to point-source stellar magnitude.
        // Give the best qualified dark-sky target one place without displacing the body priority.
        for (Candidate c : ranked) {
            if (c.kind.equals("deep_sky") && selected.size() < MAX_PICKS) {
                selected.add(c);
                break;
            }
        }
        for (Candidate c : ranked) {
            if (selected.size() >= MAX_PICKS) break;
            if (!c.kind.equals("deep_sky") && !selected.contains(c)) selected.add(c);
        }
        for (Candidate c : ranked) {
            if (selected.size() >= MAX_PICKS) break;
            if (!selected.contains(c)) selected.add(c);
        }
        List<Pick> picks = new ArrayList<>();
        for (Candidate c : selected) {
            String reason = c.kind.equals("moon")
                    ? "月面明亮易找；在本时段采样点中此时位置最高。"
                    : c.kind.equals("planet")
                    ? "明亮行星，适合从低倍率开始；此时接近本时段最高位置。"
                    : c.kind.equals("deep_sky")
                    ? "暗夜且高度合适的候选；此时接近本时段最高位置，但不代表实际可见度。"
                    : "亮星，适合辨认星空；此时接近本时段最高位置。";
            String equipment = c.kind.equals("moon") ? "肉眼或双筒望远镜"
                    : c.kind.equals("planet") ? "肉眼；小型望远镜可看更多细节"
                    : c.kind.equals("deep_sky") ? c.equipment : "肉眼";
            SolarSystem.BodyPosition moon = findBody(
                    SolarSystem.snapshot(c.time, latitude, longitude), "moon");
            String moonlightNote = moonlightNote(moon);
            String moonPhaseNote = c.kind.equals("moon") ? moonPhaseNote(moon) : null;
            picks.add(new Pick(c.id, c.name, c.kind, c.time, c.segment.start, c.segment.end,
                    c.altitude, c.azimuth, reason, equipment, moonlightNote, moonPhaseNote));
        }
        String message = picks.isEmpty()
                ? "有暮光或夜间时段，但没有目标在采样点达到20°高度。"
                : "为你挑选了 "+picks.size()+" 个较高空目标，推荐时刻为近似值。";
        return new Result(firstDark, lastDark, picks, true, message, SAMPLE_MINUTES);
    }

    private static void validate(Instant time, double latitude, double longitude) {
        if (time == null) throw new IllegalArgumentException("selectedTime");
        if (time.isBefore(Instant.ofEpochMilli(TimeAxis.MIN_MILLIS)) || time.isAfter(Instant.ofEpochMilli(TimeAxis.MAX_MILLIS)))
            throw new IllegalArgumentException("selectedTime outside supported range");
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90)
            throw new IllegalArgumentException("latitude must be in [-90,+90]");
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180)
            throw new IllegalArgumentException("longitude must be in [-180,+180]");
    }

    private static SolarSystem.BodyPosition findBody(List<SolarSystem.BodyPosition> bodies, String id) {
        for (SolarSystem.BodyPosition body : bodies) if (id.equals(body.id)) return body;
        return null;
    }

    private static boolean isSupportedBody(String id) {
        return "moon".equals(id) || "venus".equals(id) || "mars".equals(id)
                || "jupiter".equals(id) || "saturn".equals(id);
    }

    private static void consider(Map<String, Candidate> best, Candidate next) {
        Candidate old = best.get(next.id);
        if (old == null || next.altitude > old.altitude) best.put(next.id, next);
    }

    private static String moonlightNote(SolarSystem.BodyPosition moon) {
        if (moon == null) return "月光情况未知。";
        int percent = (int) Math.round(moon.illumination * 100.0);
        if (moon.altitude < 0)
            return "月球照亮约" + percent + "%、高度约" + Math.round(moon.altitude)
                    + "°（地平线下），月光影响可能较小。";
        return "月球照亮约" + percent + "%、高度约" + Math.round(moon.altitude)
                + "°，月光可能降低暗弱目标的对比度。";
    }

    private static String moonPhaseNote(SolarSystem.BodyPosition moon) {
        if (moon == null) return "月相未知。";
        int marker = moon.description.indexOf("当前为");
        return marker >= 0 ? moon.description.substring(marker) :
                "月球照亮约" + Math.round(moon.illumination * 100.0) + "%。";
    }

    private static final class Segment {
        final Instant start;
        Instant end;
        Segment(Instant time) { start = time; end = time; }
    }

    private static final class Candidate {
        final String id, name, kind;
        final Instant time;
        final double altitude, azimuth, magnitude;
        final Segment segment;
        final String equipment;
        Candidate(String id, String name, String kind, Instant time, double altitude,
                  double azimuth, double magnitude, Segment segment, String equipment) {
            this.id=id; this.name=name; this.kind=kind; this.time=time;
            this.altitude=altitude; this.azimuth=azimuth; this.magnitude=magnitude;
            this.segment=segment; this.equipment=equipment;
        }
        double score() {
            return kind.equals("deep_sky") ? altitude
                    : altitude - Math.max(0.0, magnitude + 1.0) * 2.5;
        }
    }

    public static final class Result {
        public final Instant windowStart, windowEnd;
        public final List<Pick> picks;
        public final boolean hasDarkWindow;
        public final String message;
        public final int sampleMinutes;
        private Result(Instant windowStart, Instant windowEnd, List<Pick> picks,
                       boolean hasDarkWindow, String message, int sampleMinutes) {
            this.windowStart=windowStart; this.windowEnd=windowEnd;
            this.picks=Collections.unmodifiableList(new ArrayList<>(picks));
            this.hasDarkWindow=hasDarkWindow; this.message=message; this.sampleMinutes=sampleMinutes;
        }
    }

    public static final class Pick {
        /** Stable StarCatalog or SolarSystem identifier. */
        public final String starId;
        public final String displayName, kind;
        public final Instant bestTime;
        /** Approximate continuous sampled interval containing {@link #bestTime}. */
        public final Instant windowStart, windowEnd;
        public final double altitudeDegrees, azimuthDegrees;
        public final String reason, equipment;
        /** Moon altitude/illumination context at {@link #bestTime}; does not assert visibility. */
        public final String moonlightNote;
        /** Phase context for a selected Moon, otherwise null. */
        public final String moonPhaseNote;
        private Pick(String starId, String displayName, String kind, Instant bestTime,
                     Instant windowStart, Instant windowEnd, double altitudeDegrees,
                     double azimuthDegrees, String reason, String equipment,
                     String moonlightNote, String moonPhaseNote) {
            this.starId=starId; this.displayName=displayName; this.kind=kind; this.bestTime=bestTime;
            this.altitudeDegrees=altitudeDegrees; this.azimuthDegrees=azimuthDegrees;
            this.reason=reason; this.equipment=equipment;
            this.windowStart=windowStart; this.windowEnd=windowEnd;
            this.moonlightNote=moonlightNote; this.moonPhaseNote=moonPhaseNote;
        }
    }
}
