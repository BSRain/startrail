import com.startrail.app.astro.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/** Run with assertions enabled after compiling the pure-Java astro package. */
public final class AstronomyVerification {
    private static void near(double actual, double expected, double tolerance, String label) {
        if (Math.abs(actual - expected) > tolerance)
            throw new AssertionError(label + ": got " + actual + ", expected " + expected);
    }

    private static void fixture(String label, String utc, double raHours, double decDeg,
                                double latDeg, double lonDeg, double refAlt, double refAz) {
        Astronomy.HorizontalPosition actual = Astronomy.horizontal(
                raHours, decDeg, Instant.parse(utc), latDeg, lonDeg);
        double a1 = Math.toRadians(actual.altitude), a2 = Math.toRadians(refAlt);
        double deltaAz = Math.toRadians(actual.azimuth - refAz);
        double dot = Math.sin(a1) * Math.sin(a2) + Math.cos(a1) * Math.cos(a2) * Math.cos(deltaAz);
        double separation = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
        if (separation > 0.15)
            throw new AssertionError(label + " separation " + separation + " deg; actual alt/az "
                    + actual.altitude + "/" + actual.azimuth + ", ERFA " + refAlt + "/" + refAz);
    }

    public static void main(String[] args) {
        // Unix epoch and J2000 are independent calendar/JD reference values.
        near(Astronomy.julianDate(Instant.EPOCH), 2440587.5, 1e-10, "Unix epoch JD");
        Instant j2000 = Instant.parse("2000-01-01T12:00:00Z");
        near(Astronomy.julianDate(j2000), 2451545.0, 1e-10, "J2000 JD");

        // USNO gives GMST 18.697374558 h at J2000. A dec=0 star at that RA is at zenith
        // for an equatorial Greenwich observer. This checks time, longitude, signs and transform.
        Astronomy.HorizontalPosition transit = Astronomy.horizontal(18.697374558, 0, j2000, 0, 0);
        near(transit.altitude, 90.0, 0.0001, "USNO J2000 meridian transit");

        // At Earth's north pole, altitude equals declination regardless of hour angle.
        Astronomy.HorizontalPosition pole = Astronomy.horizontal(6, 23.5, j2000, 90, 0);
        near(pole.altitude, 23.5, 1e-9, "north-pole geometry");

        // Fixed fixtures generated with ERFA eraAtco13 (SOFA-derived), pressure=0 (no
        // refraction), proper motion/parallax/radial velocity and polar motion set to zero.
        // They span 1900..2100, both hemispheres, longitude signs and the RA wrap boundary.
        fixture("c1900_west", "1900-06-01T03:04:05Z", 23.95, -20, 12.5, -155.0, -52.965320019, 252.351860217);
        fixture("j2000_greenwich", "2000-01-01T12:00:00Z", 18.697374558, 0, 0, 0, 89.994247022, 278.865356795);
        fixture("hk_sirius_2026", "2026-01-15T13:00:00Z", 6.7525, -16.7161, 22.3193, 114.1694, 36.450997053, 133.661806457);
        fixture("hk_polaris_2026", "2026-09-06T12:00:00Z", 2.5303, 89.2641, 22.3193, 114.1694, 21.939560590, 0.540167949);
        fixture("south_vega_2026", "2026-07-01T04:00:00Z", 18.6156, 38.7837, -45, 170, -53.040268714, 81.660236927);
        fixture("north_altair_2026", "2026-09-01T22:30:00Z", 19.8464, 8.8683, 65, -30, 33.550494018, 168.809081054);
        fixture("ra_zero_east", "2026-03-20T00:01:00Z", 0.001, 10, 10, 179.5, 86.990362827, 86.982802284);
        fixture("ra_wrap_west", "2026-03-20T23:59:00Z", 23.999, -35, -10, -179.5, 65.104972426, 176.997350384);
        fixture("north_extreme", "2100-01-01T00:00:00Z", 12, 70, 85, 45, 73.263123247, 134.857476531);
        fixture("south_extreme", "2100-07-01T12:00:00Z", 0.1, -70, -85, -75, 73.970276084, 331.683623610);
        fixture("c2100_mid", "2100-06-01T08:15:30Z", 14.261, 19.1824, 35, 120, 17.533421392, 79.066189532);
        fixture("c1900_south", "1900-12-15T19:30:00Z", 16.4901, -26.432, -60, 18, -0.036508154, 207.813740323);

        if (StarCatalog.stars().size() < 50) throw new AssertionError("catalog too small");
        Set<String> ids = new HashSet<>();
        for (Star s : StarCatalog.stars()) {
            if (!ids.add(s.id)) throw new AssertionError("duplicate id " + s.id);
            if (s.raHours < 0 || s.raHours >= 24 || s.decDeg < -90 || s.decDeg > 90)
                throw new AssertionError("invalid coordinates " + s.id);
        }
        if (!StarCatalog.findById("polaris").aliases.contains("北极星"))
            throw new AssertionError("common-name alias missing");
        if (StarCatalog.search("织女星").isEmpty() || !StarCatalog.search("织女星").get(0).id.equals("vega"))
            throw new AssertionError("alias search failed");
        for (ConstellationLine line : StarCatalog.lines()) {
            if (!ids.contains(line.fromId) || !ids.contains(line.toId))
                throw new AssertionError("dangling line " + line.fromId + "-" + line.toId);
        }

        // Polaris remains close to true north for Hong Kong when above the horizon.
        Star polaris = StarCatalog.findById("polaris");
        Astronomy.HorizontalPosition hp = Astronomy.horizontal(polaris, Instant.parse("2026-09-06T12:00:00Z"), 22.3193, 114.1694);
        if (hp.azimuth > 3 && hp.azimuth < 357) throw new AssertionError("Polaris azimuth " + hp.azimuth);
        near(hp.altitude, 22.3193, 1.2, "Polaris altitude near latitude");
        SkyProjection.Point east = SkyProjection.project(0, 90, 100, 100, 0);
        if (east.x >= 50) throw new AssertionError("upward sky must render east on the left");
        System.out.println("OK: ERFA fixtures; " + StarCatalog.stars().size() + " stars, " + StarCatalog.lines().size() + " lines");
    }
}
