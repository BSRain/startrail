package com.startrail.app.astro;

/** Pure geometry for smoothly advancing a cached sky snapshot between astronomy updates. */
public final class SkyMotion {
    /** Mean sidereal rotation in degrees per mean solar day, matching {@link Astronomy}. */
    public static final double SIDEREAL_DEGREES_PER_DAY = 360.98564736629;

    private SkyMotion() {}

    /**
     * Returns the equivalent camera for stars cached {@code offsetSeconds} earlier.
     * The ENU north celestial pole is {@code [0, cos(latitude), sin(latitude)]}.
     */
    public static SkyCamera.Basis forSky(SkyCamera.Basis camera, double latitudeDegrees,
                                         double offsetSeconds) {
        if (camera == null) throw new IllegalArgumentException("camera");
        if (!Double.isFinite(latitudeDegrees) || latitudeDegrees < -90.0 || latitudeDegrees > 90.0)
            throw new IllegalArgumentException("latitude");
        if (!Double.isFinite(offsetSeconds)) throw new IllegalArgumentException("offsetSeconds");

        double latitude = Math.toRadians(latitudeDegrees);
        double[] pole = {0.0, Math.cos(latitude), Math.sin(latitude)};
        double angle = Math.toRadians(offsetSeconds * SIDEREAL_DEGREES_PER_DAY / 86400.0);
        return new SkyCamera.Basis(
                rotate(camera.right, pole, angle),
                rotate(camera.up, pole, angle),
                rotate(camera.forward, pole, angle));
    }

    private static double[] rotate(double[] vector, double[] axis, double angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        double projection = dot(axis, vector) * (1.0 - c);
        double[] cross = {
                axis[1] * vector[2] - axis[2] * vector[1],
                axis[2] * vector[0] - axis[0] * vector[2],
                axis[0] * vector[1] - axis[1] * vector[0]
        };
        return new double[] {
                vector[0] * c + cross[0] * s + axis[0] * projection,
                vector[1] * c + cross[1] * s + axis[1] * projection,
                vector[2] * c + cross[2] * s + axis[2] * projection
        };
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }
}
