package com.startrail.app.astro;

/** Fixed ICRS/J2000 and Galactic coordinate conversion. */
public final class GalacticCoordinates {
    private GalacticCoordinates() {}

    public static final class Equatorial {
        public final double raHours;
        public final double decDegrees;
        Equatorial(double raHours, double decDegrees) {
            this.raHours = raHours;
            this.decDegrees = decDegrees;
        }
    }

    /* Transpose of Gaia EDR3 eq. 4.62 (ICRS -> Galactic). */
    private static final double[][] GALACTIC_TO_ICRS = {
            {-0.0548755604162154,  0.4941094278755837, -0.8676661490190047},
            {-0.8734370902348850, -0.4448296299600112, -0.1980763734312015},
            {-0.4838350155487132,  0.7469822444972189,  0.4559837761750669}
    };

    public static Equatorial galacticToEquatorial(double longitudeDegrees,
                                                   double latitudeDegrees) {
        if (!Double.isFinite(longitudeDegrees) || !Double.isFinite(latitudeDegrees)
                || latitudeDegrees < -90.0 || latitudeDegrees > 90.0) {
            throw new IllegalArgumentException("Invalid galactic coordinates");
        }
        double l = Math.toRadians(longitudeDegrees);
        double b = Math.toRadians(latitudeDegrees);
        double cb = Math.cos(b);
        double[] g = {cb * Math.cos(l), cb * Math.sin(l), Math.sin(b)};
        double x = dot(GALACTIC_TO_ICRS[0], g);
        double y = dot(GALACTIC_TO_ICRS[1], g);
        double z = dot(GALACTIC_TO_ICRS[2], g);
        double ra = Math.toDegrees(Math.atan2(y, x));
        if (ra < 0.0) ra += 360.0;
        return new Equatorial(ra / 15.0,
                Math.toDegrees(Math.atan2(z, Math.hypot(x, y))));
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }
}
