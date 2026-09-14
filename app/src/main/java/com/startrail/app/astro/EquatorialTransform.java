package com.startrail.app.astro;

import java.time.Instant;

/**
 * Immutable J2000 equatorial Cartesian to local East, North, Up transform.
 *
 * <p>The matrix combines the same IAU 1976 precession and GMST polynomial used
 * by {@link Astronomy#horizontal(double, double, Instant, double, double)}.
 * It introduces no additional astronomical approximation; it only avoids the
 * per-object conversion back through right ascension, declination, altitude,
 * and azimuth.</p>
 */
public final class EquatorialTransform {
    private final double m00, m01, m02;
    private final double m10, m11, m12;
    private final double m20, m21, m22;

    /** Builds one reusable transform for an observing time and location. */
    public EquatorialTransform(Instant time, double latitudeDegrees, double longitudeDegrees) {
        if (time == null) throw new IllegalArgumentException("time");
        if (!Double.isFinite(latitudeDegrees) || latitudeDegrees < -90.0 || latitudeDegrees > 90.0)
            throw new IllegalArgumentException("latitude");
        if (!Double.isFinite(longitudeDegrees) || longitudeDegrees < -180.0 || longitudeDegrees > 180.0)
            throw new IllegalArgumentException("longitude");

        double jd = Astronomy.julianDate(time);
        double t = (jd - 2451545.0) / 36525.0;
        double t2 = t * t, t3 = t2 * t;
        double zeta = Math.toRadians((2306.2181 * t + 0.30188 * t2 + 0.017998 * t3) / 3600.0);
        double z = Math.toRadians((2306.2181 * t + 1.09468 * t2 + 0.018203 * t3) / 3600.0);
        double theta = Math.toRadians((2004.3109 * t - 0.42665 * t2 - 0.041833 * t3) / 3600.0);

        double czeta = Math.cos(zeta), szeta = Math.sin(zeta);
        double cz = Math.cos(z), sz = Math.sin(z);
        double ct = Math.cos(theta), st = Math.sin(theta);
        // IAU 1976 precession expressed directly as a Cartesian rotation.
        double p00 = cz * ct * czeta - sz * szeta;
        double p01 = -cz * ct * szeta - sz * czeta;
        double p02 = -cz * st;
        double p10 = sz * ct * czeta + cz * szeta;
        double p11 = -sz * ct * szeta + cz * czeta;
        double p12 = -sz * st;
        double p20 = st * czeta;
        double p21 = -st * szeta;
        double p22 = ct;

        double gmst = 280.46061837 + 360.98564736629 * (jd - 2451545.0)
                + 0.000387933 * t2 - t3 / 38710000.0;
        double lst = Math.toRadians(Astronomy.normalizeDegrees(gmst + longitudeDegrees));
        double cl = Math.cos(lst), sl = Math.sin(lst);
        double latitude = Math.toRadians(latitudeDegrees);
        double cp = Math.cos(latitude), sp = Math.sin(latitude);

        // Rows of the equatorial-of-date to ENU rotation.
        double q00 = -sl, q01 = cl, q02 = 0.0;
        double q10 = -sp * cl, q11 = -sp * sl, q12 = cp;
        double q20 = cp * cl, q21 = cp * sl, q22 = sp;

        m00 = q00*p00 + q01*p10 + q02*p20;
        m01 = q00*p01 + q01*p11 + q02*p21;
        m02 = q00*p02 + q01*p12 + q02*p22;
        m10 = q10*p00 + q11*p10 + q12*p20;
        m11 = q10*p01 + q11*p11 + q12*p21;
        m12 = q10*p02 + q11*p12 + q12*p22;
        m20 = q20*p00 + q21*p10 + q22*p20;
        m21 = q20*p01 + q21*p11 + q22*p21;
        m22 = q20*p02 + q21*p12 + q22*p22;
    }

    /** Returns a unit J2000 vector for catalogue right ascension and declination. */
    public static double[] fromRaDec(double raHours, double decDegrees) {
        validateRaDec(raHours, decDegrees);
        double ra = Math.toRadians(raHours * 15.0), dec = Math.toRadians(decDegrees);
        double cosDec = Math.cos(dec);
        return new double[] {cosDec * Math.cos(ra), cosDec * Math.sin(ra), Math.sin(dec)};
    }

    /**
     * Transforms one vector into the first three slots of a caller-owned array.
     * Any additional output elements (for example a packed magnitude) are preserved.
     */
    public void transform(double[] input, double[] output) {
        transform(input, 0, output, 0);
    }

    /** Transforms one vector into a caller-owned array, permitting in-place use. */
    public void transform(double[] input, int inputOffset, double[] output, int outputOffset) {
        requireRange(input, inputOffset, "input");
        requireRange(output, outputOffset, "output");
        double x = input[inputOffset], y = input[inputOffset + 1], z = input[inputOffset + 2];
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("input vector must be finite");
        output[outputOffset] = m00*x + m01*y + m02*z;
        output[outputOffset + 1] = m10*x + m11*y + m12*z;
        output[outputOffset + 2] = m20*x + m21*y + m22*z;
    }

    /** Transforms packed xyz vectors without allocating per vector. */
    public void transform(double[] input, int inputOffset, double[] output, int outputOffset, int vectorCount) {
        if (vectorCount < 0) throw new IllegalArgumentException("vectorCount");
        requireRange(input, inputOffset, vectorCount, "input");
        requireRange(output, outputOffset, vectorCount, "output");
        if (input == output && outputOffset > inputOffset && outputOffset < inputOffset + vectorCount * 3)
            throw new IllegalArgumentException("overlapping ranges require outputOffset <= inputOffset");
        for (int i = 0; i < vectorCount; i++)
            transform(input, inputOffset + i * 3, output, outputOffset + i * 3);
    }

    private static void validateRaDec(double raHours, double decDegrees) {
        if (!Double.isFinite(raHours) || raHours < 0.0 || raHours >= 24.0)
            throw new IllegalArgumentException("raHours");
        if (!Double.isFinite(decDegrees) || decDegrees < -90.0 || decDegrees > 90.0)
            throw new IllegalArgumentException("declination");
    }

    private static void requireRange(double[] values, int offset, String name) {
        requireRange(values, offset, 1, name);
    }

    private static void requireRange(double[] values, int offset, int count, String name) {
        long end = (long) offset + (long) count * 3L;
        if (values == null || offset < 0 || end > values.length)
            throw new IllegalArgumentException(name + " range");
    }
}
