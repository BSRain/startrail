package com.startrail.app.astro;

import java.time.Instant;

/** Lightweight finder-chart calculations. Includes J2000 precession; omits nutation, refraction and proper motion. */
public final class Astronomy {
    private Astronomy() {}

    public static final class HorizontalPosition {
        public final double altitudeDegrees;
        public final double altitude;
        /** Degrees clockwise from true north: north=0, east=90. */
        public final double azimuthDegrees;
        public final double azimuth;
        public final boolean visible;

        public HorizontalPosition(double altitudeDegrees, double azimuthDegrees) {
            this.altitudeDegrees = altitudeDegrees;
            this.azimuthDegrees = normalizeDegrees(azimuthDegrees);
            this.altitude = altitudeDegrees;
            this.azimuth = this.azimuthDegrees;
            this.visible = altitudeDegrees >= 0.0;
        }

        public boolean isAboveHorizon() { return altitudeDegrees >= 0.0; }
    }

    public static HorizontalPosition horizontal(Star star, Instant time,
                                                double latitudeDegrees, double longitudeDegrees) {
        return horizontal(star.raHours, star.decDegrees, time, latitudeDegrees, longitudeDegrees);
    }

    public static HorizontalPosition horizontal(double raHours, double decDegrees, Instant time,
                                                double latitudeDegrees, double longitudeDegrees) {
        if (time == null) throw new IllegalArgumentException("time");
        if (!Double.isFinite(raHours) || raHours < 0 || raHours >= 24) throw new IllegalArgumentException("raHours");
        if (!Double.isFinite(decDegrees) || decDegrees < -90 || decDegrees > 90) throw new IllegalArgumentException("declination");
        if (!Double.isFinite(latitudeDegrees) || !Double.isFinite(longitudeDegrees)) throw new IllegalArgumentException("location");
        if (latitudeDegrees < -90 || latitudeDegrees > 90) throw new IllegalArgumentException("latitude");
        if (longitudeDegrees < -180 || longitudeDegrees > 180) throw new IllegalArgumentException("longitude");
        double jd = julianDate(time);
        double t = (jd - 2451545.0) / 36525.0;
        double[] ofDate = precessJ2000(raHours, decDegrees, t);
        // IAU-compatible polynomial for Greenwich mean sidereal time, adequate for a visual star map.
        double gmst = 280.46061837 + 360.98564736629 * (jd - 2451545.0)
                + 0.000387933 * t * t - t * t * t / 38710000.0;
        double hourAngle = Math.toRadians(normalizeSignedDegrees(gmst + longitudeDegrees - ofDate[0] * 15.0));
        double lat = Math.toRadians(latitudeDegrees);
        double dec = Math.toRadians(ofDate[1]);
        double sinAlt = Math.sin(dec) * Math.sin(lat) + Math.cos(dec) * Math.cos(lat) * Math.cos(hourAngle);
        double alt = Math.asin(clamp(sinAlt, -1, 1));
        // atan2 form stays stable near meridian; result is measured clockwise from north.
        double az = Math.atan2(-Math.sin(hourAngle) * Math.cos(dec),
                Math.sin(dec) * Math.cos(lat) - Math.cos(dec) * Math.sin(lat) * Math.cos(hourAngle));
        return new HorizontalPosition(Math.toDegrees(alt), Math.toDegrees(az));
    }

    public static double julianDate(Instant instant) {
        if (instant == null) throw new IllegalArgumentException("instant");
        return 2440587.5 + instant.getEpochSecond() / 86400.0 + instant.getNano() / 86400_000_000_000.0;
    }

    public static double normalizeDegrees(double degrees) {
        double x = degrees % 360.0;
        return x < 0 ? x + 360.0 : x;
    }

    private static double normalizeSignedDegrees(double degrees) {
        double x = normalizeDegrees(degrees);
        return x > 180 ? x - 360 : x;
    }

    /** IAU 1976 precession, sufficient for this compact J2000 catalogue and modern observing dates. */
    static double[] precessJ2000(double raHours, double decDegrees, double centuries) {
        double t2 = centuries * centuries, t3 = t2 * centuries;
        double zeta = Math.toRadians((2306.2181 * centuries + 0.30188 * t2 + 0.017998 * t3) / 3600.0);
        double z = Math.toRadians((2306.2181 * centuries + 1.09468 * t2 + 0.018203 * t3) / 3600.0);
        double theta = Math.toRadians((2004.3109 * centuries - 0.42665 * t2 - 0.041833 * t3) / 3600.0);
        double ra = Math.toRadians(raHours * 15.0), dec = Math.toRadians(decDegrees);
        double a = Math.cos(dec) * Math.sin(ra + zeta);
        double b = Math.cos(theta) * Math.cos(dec) * Math.cos(ra + zeta) - Math.sin(theta) * Math.sin(dec);
        double c = Math.sin(theta) * Math.cos(dec) * Math.cos(ra + zeta) + Math.cos(theta) * Math.sin(dec);
        double outRa = normalizeDegrees(Math.toDegrees(Math.atan2(a, b) + z)) / 15.0;
        return new double[] { outRa, Math.toDegrees(Math.asin(clamp(c, -1, 1))) };
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
