package com.startrail.app.astro;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CancellationException;

import io.github.cosinekitty.astronomy.Time;
import io.github.cosinekitty.astronomy.Vector;

/** Major annual meteor-shower maxima from the IMO 2026 and 2027 calendars. */
public final class MeteorShowers43 {
    public static final int FIRST_SUPPORTED_YEAR = 2026;
    public static final int LAST_SUPPORTED_YEAR = 2027;

    private static final Duration HALF_WINDOW = Duration.ofHours(36);
    private static final Duration MAX_RANGE = Duration.ofDays(366);
    private static final String SOURCE_2026 =
            "https://www.imo.net/files/meteor-shower/cal2026.pdf";
    private static final String SOURCE_2027 =
            "https://www.imo.net/resources/calendar/ShCal27s.pdf";

    // Solar longitude and approximate J2000 radiant at the customary maximum.
    // These are planning coordinates: each radiant drifts over its activity period.
    private static final Shower[] SHOWERS = {
            new Shower("QUA", "象限仪座", 283.15, 230.0 / 15.0, +49.0, 1, 4),
            new Shower("LYR", "天琴座", 32.32, 271.0 / 15.0, +34.0, 4, 22),
            new Shower("ETA", "宝瓶座η", 45.5, 338.0 / 15.0, -1.0, 5, 6),
            new Shower("SDA", "南宝瓶座δ", 128.0, 340.0 / 15.0, -16.0, 7, 31),
            new Shower("PER", "英仙座", 140.05, 48.0 / 15.0, +58.0, 8, 13),
            new Shower("ORI", "猎户座", 208.0, 95.0 / 15.0, +16.0, 10, 21),
            new Shower("LEO", "狮子座", 235.27, 152.0 / 15.0, +22.0, 11, 17),
            new Shower("GEM", "双子座", 262.2, 112.0 / 15.0, +33.0, 12, 14)
    };

    private MeteorShowers43() {}

    /** Returns immutable events whose estimated peaks lie in {@code [start,end)}. */
    public static List<CelestialEvents.Event> search(Instant start, Instant end) {
        validate(start, end);
        checkCancelled();
        ArrayList<CelestialEvents.Event> result = new ArrayList<>();
        int firstYear = Math.max(FIRST_SUPPORTED_YEAR,
                ZonedDateTime.ofInstant(start, ZoneOffset.UTC).getYear() - 1);
        int lastYear = Math.min(LAST_SUPPORTED_YEAR,
                ZonedDateTime.ofInstant(end.minusNanos(1), ZoneOffset.UTC).getYear() + 1);
        for (int year = firstYear; year <= lastYear; year++) {
            for (Shower shower : SHOWERS) {
                checkCancelled();
                Instant peak = peak(year, shower);
                if (!peak.isBefore(start) && peak.isBefore(end))
                    result.add(event(year, shower, peak));
            }
        }
        result.sort(Comparator.comparing((CelestialEvents.Event event) -> event.peak)
                .thenComparing(event -> event.id));
        return Collections.unmodifiableList(result);
    }

    private static Instant peak(int year, Shower shower) {
        ZonedDateTime seed = ZonedDateTime.of(year, shower.month, shower.day,
                0, 0, 0, 0, ZoneOffset.UTC).minusDays(4);
        Time left = Time.fromMillisecondsSince1970(seed.toInstant().toEpochMilli());
        Time right = left.addDays(10.0);
        double leftOffset = longitudeOffset(solarLongitudeJ2000(left), shower.solarLongitude);
        double rightOffset = longitudeOffset(solarLongitudeJ2000(right), shower.solarLongitude);
        if (leftOffset >= 0.0 || rightOffset <= 0.0)
            throw new IllegalStateException("could not bracket solar longitude for " + shower.code);

        // IMO tabulates solar longitude at equinox J2000.0. Astronomy Engine's
        // searchSunLongitude instead uses true ecliptic/equinox of date, whose
        // precession would shift these 2026/2027 maxima by about nine hours.
        for (int i = 0; i < 48; i++) {
            checkCancelled();
            Time middle = left.addDays((right.getUt() - left.getUt()) / 2.0);
            if (longitudeOffset(solarLongitudeJ2000(middle), shower.solarLongitude) < 0.0)
                left = middle;
            else
                right = middle;
        }
        return Instant.ofEpochMilli(right.toMillisecondsSince1970());
    }

    static double solarLongitudeJ2000(Time time) {
        Vector sunEqj = io.github.cosinekitty.astronomy.Astronomy.geoVector(
                io.github.cosinekitty.astronomy.Body.Sun, time,
                io.github.cosinekitty.astronomy.Aberration.Corrected);
        Vector sunEcl = io.github.cosinekitty.astronomy.Astronomy.rotationEqjEcl().rotate(sunEqj);
        return sunEcl.toSpherical().getLon();
    }

    private static double longitudeOffset(double longitude, double target) {
        double offset = (longitude - target) % 360.0;
        if (offset < -180.0) offset += 360.0;
        if (offset >= 180.0) offset -= 360.0;
        return offset;
    }

    private static CelestialEvents.Event event(int year, Shower shower, Instant peak) {
        String description = "按 IMO 年度日历所列太阳黄经推算的通常峰值；实际峰时与强度会逐年变化，"
                + "天气、月光和辐射点高度也会影响观测，本事件不保证出现可见流星。"
                + "辐射点为峰期附近的近似 J2000 坐标，并会随日期漂移。";
        return new CelestialEvents.Event(
                "meteor-" + shower.code + "-" + year,
                shower.chineseName + "流星雨（预计峰期）",
                description,
                year == 2026 ? SOURCE_2026 : SOURCE_2027,
                CelestialEvents.Kind.METEOR_SHOWER,
                peak, peak.minus(HALF_WINDOW), peak.plus(HALF_WINDOW),
                "", "", true, true, -1, Double.NaN,
                shower.raHours, shower.decDegrees, Collections.emptyList());
    }

    private static void validate(Instant start, Instant end) {
        if (start == null || end == null)
            throw new IllegalArgumentException("start and end required");
        if (!start.isBefore(end))
            throw new IllegalArgumentException("start must precede end");
        if (Duration.between(start, end).compareTo(MAX_RANGE) > 0)
            throw new IllegalArgumentException("range may not exceed 366 days");
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted())
            throw new CancellationException("meteor-shower search interrupted");
    }

    private static final class Shower {
        final String code, chineseName;
        final double solarLongitude, raHours, decDegrees;
        final int month, day;

        Shower(String code, String chineseName, double solarLongitude,
               double raHours, double decDegrees, int month, int day) {
            this.code = code;
            this.chineseName = chineseName;
            this.solarLongitude = solarLongitude;
            this.raHours = raHours;
            this.decDegrees = decDegrees;
            this.month = month;
            this.day = day;
        }
    }
}
