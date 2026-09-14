package com.startrail.app.astro;

/** Azimuthal-equidistant upward-looking sky projection: zenith at centre, east on the left. */
public final class SkyProjection {
    private SkyProjection() {}

    public static final class Point {
        public final double x, y;
        public final boolean visible;
        Point(double x, double y, boolean visible) { this.x = x; this.y = y; this.visible = visible; }
    }

    public static Point project(double altitudeDegrees, double azimuthDegrees,
                                double width, double height, double rotationDegrees) {
        double radius = Math.min(width, height) * 0.48;
        double r = (90.0 - altitudeDegrees) / 90.0 * radius;
        double angle = Math.toRadians(azimuthDegrees - rotationDegrees);
        return new Point(width / 2.0 - r * Math.sin(angle), height / 2.0 - r * Math.cos(angle),
                altitudeDegrees >= 0.0 && altitudeDegrees <= 90.0);
    }
}
