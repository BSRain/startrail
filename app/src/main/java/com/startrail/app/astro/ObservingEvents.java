package com.startrail.app.astro;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;

import io.github.cosinekitty.astronomy.Aberration;
import io.github.cosinekitty.astronomy.Body;
import io.github.cosinekitty.astronomy.EquatorEpoch;
import io.github.cosinekitty.astronomy.Equatorial;
import io.github.cosinekitty.astronomy.Observer;
import io.github.cosinekitty.astronomy.Time;

/** Geometric horizon events and a 24-hour altitude curve for a catalogue target. */
public final class ObservingEvents {
    private static final Duration WINDOW = Duration.ofHours(24);
    private static final Duration STEP = Duration.ofMinutes(10);
    private static final long EVENT_TOLERANCE_SECONDS = 30;
    private static final double DAYS_1970_TO_J2000 = 10_957.5;

    private ObservingEvents() {}

    /**
     * Calculates events in the closed interval {@code [start,start+24h]}, clipped at
     * {@link TimeAxis#MAX_MILLIS}. Rise and set use the object's geometric centre at
     * altitude 0 degrees (no refraction). Transit is the true upper-meridian crossing,
     * not merely the highest sampled altitude.
     */
    public static Result calculate(Star target, Instant start, double latitude, double longitude) {
        validate(target, start, latitude, longitude);
        final Instant requestedEnd;
        try { requestedEnd = start.plus(WINDOW); }
        catch (DateTimeException | ArithmeticException ex) {
            throw new IllegalArgumentException("start does not allow a 24-hour window", ex);
        }

        Instant maximum = Instant.ofEpochMilli(TimeAxis.MAX_MILLIS);
        final Instant end = requestedEnd.isAfter(maximum) ? maximum : requestedEnd;
        Evaluator evaluator = new Evaluator(target, latitude, longitude);
        List<Sample> samples = new ArrayList<>(145);
        Instant rise = null, set = null, transit = null;
        Point previous = null;
        double previousUnwrappedHa = Double.NaN;

        for (Instant time = start; ; ) {
            checkCancelled();
            Point point = evaluator.at(time);
            samples.add(new Sample(time, point.altitude, point.sunAltitude));
            if (previous != null) {
                if (rise == null && previous.altitude < 0.0 && point.altitude >= 0.0)
                    rise = refineAltitude(evaluator, previous.time, time);
                if (set == null && previous.altitude >= 0.0 && point.altitude < 0.0)
                    set = refineAltitude(evaluator, previous.time, time);

                double delta = signedDegrees(point.hourAngle - previous.hourAngle);
                double unwrapped = previousUnwrappedHa + delta;
                if (transit == null && previousUnwrappedHa <= 0.0 && unwrapped >= 0.0)
                    transit = refineTransit(evaluator, previous.time, time,
                            previousUnwrappedHa, unwrapped);
                previousUnwrappedHa = unwrapped;
            } else {
                // Put the first sample on the branch whose zero is the next upper transit.
                previousUnwrappedHa = point.hourAngle > 0.0 ? point.hourAngle - 360.0 : point.hourAngle;
                if (Math.abs(point.hourAngle) < 1.0e-12) transit = time;
            }
            previous = point;
            if (time.equals(end)) break;
            Instant next = time.plus(STEP);
            time = next.isAfter(end) ? end : next;
        }
        double transitAltitude = transit == null ? Double.NaN : evaluator.at(transit).altitude;
        return new Result(start, end, rise, set, transit, transitAltitude, samples);
    }

    private static Instant refineAltitude(Evaluator evaluator, Instant lo, Instant hi) {
        double loAltitude = evaluator.at(lo).altitude;
        while (Duration.between(lo, hi).getSeconds() > EVENT_TOLERANCE_SECONDS) {
            checkCancelled();
            Instant mid = midpoint(lo, hi);
            double altitude = evaluator.at(mid).altitude;
            if ((loAltitude < 0.0) == (altitude < 0.0)) { lo = mid; loAltitude = altitude; }
            else hi = mid;
        }
        return midpoint(lo, hi);
    }

    private static Instant refineTransit(Evaluator evaluator, Instant lo, Instant hi,
                                         double loHa, double hiHa) {
        while (Duration.between(lo, hi).getSeconds() > EVENT_TOLERANCE_SECONDS) {
            checkCancelled();
            Instant mid = midpoint(lo, hi);
            double fraction = (double) Duration.between(lo, mid).toNanos()
                    / Duration.between(lo, hi).toNanos();
            double expected = loHa + (hiHa - loHa) * fraction;
            double midHa = loHa + signedDegrees(evaluator.at(mid).hourAngle
                    - evaluator.at(lo).hourAngle);
            // Keep the branch nearest the interpolated, unwrapped hour angle.
            midHa += 360.0 * Math.rint((expected - midHa) / 360.0);
            if (midHa < 0.0) { lo = mid; loHa = midHa; }
            else { hi = mid; hiHa = midHa; }
        }
        return midpoint(lo, hi);
    }

    private static Instant midpoint(Instant a, Instant b) {
        return a.plusNanos(Duration.between(a, b).toNanos() / 2);
    }

    private static void validate(Star target, Instant start, double latitude, double longitude) {
        if (target == null) throw new IllegalArgumentException("target");
        if (start == null) throw new IllegalArgumentException("start");
        Instant minimum = Instant.ofEpochMilli(TimeAxis.MIN_MILLIS);
        Instant maximum = Instant.ofEpochMilli(TimeAxis.MAX_MILLIS);
        if (start.isBefore(minimum) || start.isAfter(maximum))
            throw new IllegalArgumentException("start outside supported time axis");
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0)
            throw new IllegalArgumentException("latitude must be in [-90,+90]");
        if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0)
            throw new IllegalArgumentException("longitude must be in [-180,+180]");
        if (body(target.id) == null) {
            if (!Double.isFinite(target.raHours) || target.raHours < 0.0 || target.raHours >= 24.0)
                throw new IllegalArgumentException("target right ascension");
            if (!Double.isFinite(target.decDegrees) || target.decDegrees < -90.0 || target.decDegrees > 90.0)
                throw new IllegalArgumentException("target declination");
        }
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted())
            throw new CancellationException("observing-event calculation interrupted");
    }

    private static double signedDegrees(double degrees) {
        double value = Astronomy.normalizeDegrees(degrees);
        return value > 180.0 ? value - 360.0 : value;
    }

    private static Body body(String id) {
        if (id == null) return null;
        switch (id) {
            case "sun": return Body.Sun;
            case "moon": return Body.Moon;
            case "mercury": return Body.Mercury;
            case "venus": return Body.Venus;
            case "mars": return Body.Mars;
            case "jupiter": return Body.Jupiter;
            case "saturn": return Body.Saturn;
            case "uranus": return Body.Uranus;
            case "neptune": return Body.Neptune;
            default: return null;
        }
    }

    private static final class Evaluator {
        final Star target;
        final double latitude, longitude;
        final Body dynamicBody;
        final Observer observer;

        Evaluator(Star target, double latitude, double longitude) {
            this.target = target; this.latitude = latitude; this.longitude = longitude;
            dynamicBody = body(target.id);
            observer = new Observer(latitude, longitude, 0.0);
        }

        Point at(Instant instant) {
            checkCancelled();
            List<SolarSystem.BodyPosition> snapshot = SolarSystem.snapshot(instant, latitude, longitude);
            double sunAltitude = find(snapshot, "sun").altitude;
            double altitude;
            double ra;
            if (dynamicBody != null) {
                altitude = find(snapshot, target.id).altitude;
                Equatorial eq = io.github.cosinekitty.astronomy.Astronomy.equator(
                        dynamicBody, engineTime(instant), observer,
                        EquatorEpoch.OfDate, Aberration.Corrected);
                ra = eq.getRa();
            } else {
                altitude = Astronomy.horizontal(target, instant, latitude, longitude).altitudeDegrees;
                double centuries = (Astronomy.julianDate(instant) - 2451545.0) / 36525.0;
                ra = Astronomy.precessJ2000(target.raHours, target.decDegrees, centuries)[0];
            }
            double siderealDegrees = dynamicBody == null ? gmst(instant)
                    : io.github.cosinekitty.astronomy.Astronomy.siderealTime(engineTime(instant)) * 15.0;
            double hourAngle = signedDegrees(siderealDegrees + longitude - ra * 15.0);
            return new Point(instant, altitude, sunAltitude, hourAngle);
        }

        private static SolarSystem.BodyPosition find(List<SolarSystem.BodyPosition> positions, String id) {
            for (SolarSystem.BodyPosition position : positions) if (id.equals(position.id)) return position;
            throw new IllegalStateException("missing solar-system body: " + id);
        }
    }

    private static Time engineTime(Instant instant) {
        double unixDays = instant.getEpochSecond() / 86_400.0
                + instant.getNano() / 86_400_000_000_000.0;
        return new Time(unixDays - DAYS_1970_TO_J2000);
    }

    private static double gmst(Instant instant) {
        double jd = Astronomy.julianDate(instant);
        double t = (jd - 2451545.0) / 36525.0;
        return Astronomy.normalizeDegrees(280.46061837 + 360.98564736629 * (jd - 2451545.0)
                + 0.000387933 * t * t - t * t * t / 38710000.0);
    }

    private static final class Point {
        final Instant time;
        final double altitude, sunAltitude, hourAngle;
        Point(Instant time, double altitude, double sunAltitude, double hourAngle) {
            this.time = time; this.altitude = altitude; this.sunAltitude = sunAltitude;
            this.hourAngle = hourAngle;
        }
    }

    public static final class Sample {
        public final Instant time;
        public final double altitude;
        public final double sunAltitude;
        public Sample(Instant time, double altitude, double sunAltitude) {
            this.time = time; this.altitude = altitude; this.sunAltitude = sunAltitude;
        }
    }

    public static final class Result {
        public final Instant start, end, rise, set, transit;
        public final double transitAltitude;
        public final List<Sample> samples;
        private Result(Instant start, Instant end, Instant rise, Instant set, Instant transit,
                       double transitAltitude, List<Sample> samples) {
            this.start = start; this.end = end; this.rise = rise; this.set = set;
            this.transit = transit; this.transitAltitude = transitAltitude;
            this.samples = Collections.unmodifiableList(new ArrayList<>(samples));
        }
    }
}
