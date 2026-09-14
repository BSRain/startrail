package com.startrail.app.astro;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.cosinekitty.astronomy.Aberration;
import io.github.cosinekitty.astronomy.Body;
import io.github.cosinekitty.astronomy.EquatorEpoch;
import io.github.cosinekitty.astronomy.Equatorial;
import io.github.cosinekitty.astronomy.IlluminationInfo;
import io.github.cosinekitty.astronomy.Observer;
import io.github.cosinekitty.astronomy.Refraction;
import io.github.cosinekitty.astronomy.Time;
import io.github.cosinekitty.astronomy.Topocentric;

import static io.github.cosinekitty.astronomy.Astronomy.equator;
import static io.github.cosinekitty.astronomy.Astronomy.horizon;
import static io.github.cosinekitty.astronomy.Astronomy.illumination;
import static io.github.cosinekitty.astronomy.Astronomy.moonPhase;

/** Offline topocentric ephemeris for the Sun, Moon, and naked-eye/common planets. */
public final class SolarSystem {
    private static final double KM_PER_AU = 149_597_870.69098932;
    private static final double DAYS_1970_TO_J2000 = 10_957.5;

    private static final Definition[] DEFINITIONS = {
            new Definition("sun", "太阳", "Sun", "sun", Body.Sun, 695_700,
                    "太阳是太阳系的恒星，也是地球光与热的主要来源。"),
            new Definition("moon", "月球", "Moon", "moon", Body.Moon, 1_737.4,
                    "月球是地球唯一的天然卫星；月相随日、地、月的相对位置变化。"),
            new Definition("mercury", "水星", "Mercury", "planet", Body.Mercury, 2_439.7,
                    "水星是最靠近太阳的行星，通常只能在晨昏低空看见。"),
            new Definition("venus", "金星", "Venus", "planet", Body.Venus, 6_051.8,
                    "金星常称启明星或长庚星，是夜空中最明亮的行星。"),
            new Definition("mars", "火星", "Mars", "planet", Body.Mars, 3_389.5,
                    "火星因富含氧化铁的表面而呈红色。"),
            new Definition("jupiter", "木星", "Jupiter", "planet", Body.Jupiter, 69_911,
                    "木星是太阳系最大的行星，明亮且拥有显著的云带。"),
            new Definition("saturn", "土星", "Saturn", "planet", Body.Saturn, 58_232,
                    "土星以宽阔明亮的行星环闻名。"),
            new Definition("uranus", "天王星", "Uranus", "planet", Body.Uranus, 25_362,
                    "天王星是一颗冰巨星，自转轴几乎躺在轨道平面上。"),
            new Definition("neptune", "海王星", "Neptune", "planet", Body.Neptune, 24_622,
                    "海王星是距离太阳最远的主要行星，需要光学设备观测。")
    };

    private SolarSystem() {}

    /**
     * Calculates an immutable snapshot at sea level. Latitude is north-positive in [-90,+90];
     * longitude is east-positive in [-180,+180]. Altitude is geometric (no refraction).
     */
    public static List<BodyPosition> snapshot(Instant instant, double latitude, double longitude) {
        if (instant == null) throw new IllegalArgumentException("instant");
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90)
            throw new IllegalArgumentException("latitude must be in [-90,+90]");
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180)
            throw new IllegalArgumentException("longitude must be in [-180,+180]");

        // Astronomy Engine Time.ut is UT days from J2000. Avoid Instant.toEpochMilli overflow.
        double unixDays = instant.getEpochSecond() / 86_400.0
                + instant.getNano() / 86_400_000_000_000.0;
        Time time = new Time(unixDays - DAYS_1970_TO_J2000);
        Observer observer = new Observer(latitude, longitude, 0.0);
        double lunarPhase = moonPhase(time);
        List<BodyPosition> result = new ArrayList<>(DEFINITIONS.length);

        for (Definition definition : DEFINITIONS) {
            // Horizon requires true equator-of-date coordinates. equator() also subtracts the
            // observer vector, providing the Moon's important topocentric parallax correction.
            Equatorial eq = equator(definition.body, time, observer,
                    EquatorEpoch.OfDate, Aberration.Corrected);
            Topocentric horizontal = horizon(time, observer, eq.getRa(), eq.getDec(), Refraction.None);
            IlluminationInfo light = illumination(definition.body, time);
            double distanceKm = eq.getDist() * KM_PER_AU;
            double diameter = Math.toDegrees(2.0 * Math.asin(
                    Math.min(1.0, definition.radiusKm / distanceKm)));
            double fraction = definition.body == Body.Sun ? 1.0 : light.getPhaseFraction();
            String description = definition.description;
            if (definition.body == Body.Moon) description += " 当前为" + phaseName(lunarPhase) + "。";
            result.add(new BodyPosition(definition.id, definition.name, definition.englishName,
                    definition.kind, horizontal.getAltitude(), horizontal.getAzimuth(), light.getMag(),
                    fraction, distanceKm, description, diameter));
        }
        return Collections.unmodifiableList(result);
    }

    private static String phaseName(double degrees) {
        if (degrees < 22.5 || degrees >= 337.5) return "新月";
        if (degrees < 67.5) return "娥眉月";
        if (degrees < 112.5) return "上弦月";
        if (degrees < 157.5) return "盈凸月";
        if (degrees < 202.5) return "满月";
        if (degrees < 247.5) return "亏凸月";
        if (degrees < 292.5) return "下弦月";
        return "残月";
    }

    /** Immutable display model. Angles are degrees; distance is kilometers; illumination is 0..1. */
    public static final class BodyPosition {
        public final String id;
        public final String name;
        public final String englishName;
        /** Stable category: {@code sun}, {@code moon}, or {@code planet}. */
        public final String kind;
        /** Geometric degrees above the local horizon; negative values are below it. */
        public final double altitude;
        /** Degrees clockwise from true north: north=0, east=90. */
        public final double azimuth;
        /** Modelled apparent visual magnitude; smaller values are brighter. */
        public final double magnitude;
        /** Illuminated fraction of the visible disk in [0,1]. The Sun is always 1. */
        public final double illumination;
        /** Topocentric center-to-center distance in kilometers. */
        public final double distanceKm;
        public final String description;
        /** Apparent full disk diameter in degrees (equivalent spherical radius). */
        public final double angularDiameterDegrees;

        public BodyPosition(String id, String name, String englishName, String kind,
                            double altitude, double azimuth, double magnitude, double illumination,
                            double distanceKm, String description, double angularDiameterDegrees) {
            this.id = id;
            this.name = name;
            this.englishName = englishName;
            this.kind = kind;
            this.altitude = altitude;
            this.azimuth = azimuth;
            this.magnitude = magnitude;
            this.illumination = illumination;
            this.distanceKm = distanceKm;
            this.description = description;
            this.angularDiameterDegrees = angularDiameterDegrees;
        }

        public boolean isAboveHorizon() { return altitude >= 0.0; }
        public String displayName() { return name + " · " + englishName; }
    }

    private static final class Definition {
        final String id, name, englishName, kind, description;
        final Body body;
        final double radiusKm;

        Definition(String id, String name, String englishName, String kind, Body body,
                   double radiusKm, String description) {
            this.id = id;
            this.name = name;
            this.englishName = englishName;
            this.kind = kind;
            this.body = body;
            this.radiusKm = radiusKm;
            this.description = description;
        }
    }
}
