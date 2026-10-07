package com.startrail.app.astro;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CancellationException;

import io.github.cosinekitty.astronomy.Aberration;
import io.github.cosinekitty.astronomy.Body;
import io.github.cosinekitty.astronomy.EclipseEvent;
import io.github.cosinekitty.astronomy.EclipseKind;
import io.github.cosinekitty.astronomy.GlobalSolarEclipseInfo;
import io.github.cosinekitty.astronomy.LocalSolarEclipseInfo;
import io.github.cosinekitty.astronomy.LunarEclipseInfo;
import io.github.cosinekitty.astronomy.MoonQuarterInfo;
import io.github.cosinekitty.astronomy.Observer;
import io.github.cosinekitty.astronomy.Time;
import io.github.cosinekitty.astronomy.Vector;

/** Calculates a bounded catalogue of astronomical events using Astronomy Engine. */
public final class CelestialEvents {
    private static final String SOURCE =
            "https://github.com/cosinekitty/astronomy/blob/master/source/kotlin/README.md";
    private static final Duration EVENT_HALF_WINDOW = Duration.ofHours(18);
    private static final Duration PHASE_HALF_WINDOW = Duration.ofHours(36);
    private static final Duration COARSE_STEP = Duration.ofHours(6);
    private static final Instant MIN_TIME = Instant.parse("1900-01-01T00:00:00Z");
    private static final Instant MAX_TIME = Instant.parse("2101-01-01T00:00:00Z");
    private static final Body[] PLANETS = {
            Body.Mercury, Body.Venus, Body.Mars, Body.Jupiter, Body.Saturn
    };

    private CelestialEvents() {}

    public enum Kind {
        SOLAR_ECLIPSE, LUNAR_ECLIPSE, MOON_PHASE, CLOSE_APPROACH, METEOR_SHOWER
    }

    /** Immutable event record shared by the event planner and calendar UI. */
    public static final class Event {
        public final String id, title, description, sourceUrl;
        public final Kind kind;
        public final Instant peak, start, end;
        public final String primaryId, secondaryId;
        public final boolean approximate, local;
        public final int phase;
        public final double separationDegrees, radiantRaHours, radiantDecDegrees;
        public final List<Contact> contacts;

        public Event(String id, String title, String description, String sourceUrl, Kind kind,
                     Instant peak, Instant start, Instant end, String primaryId,
                     String secondaryId, boolean approximate, boolean local, int phase,
                     double separationDegrees, double radiantRaHours, double radiantDecDegrees,
                     List<Contact> contacts) {
            this.id = Objects.requireNonNull(id, "id");
            this.title = Objects.requireNonNull(title, "title");
            this.description = Objects.requireNonNull(description, "description");
            this.sourceUrl = Objects.requireNonNull(sourceUrl, "sourceUrl");
            this.kind = Objects.requireNonNull(kind, "kind");
            this.peak = Objects.requireNonNull(peak, "peak");
            this.start = Objects.requireNonNull(start, "start");
            this.end = Objects.requireNonNull(end, "end");
            this.primaryId = Objects.requireNonNull(primaryId, "primaryId");
            this.secondaryId = Objects.requireNonNull(secondaryId, "secondaryId");
            this.approximate = approximate;
            this.local = local;
            this.phase = phase;
            this.separationDegrees = separationDegrees;
            this.radiantRaHours = radiantRaHours;
            this.radiantDecDegrees = radiantDecDegrees;
            this.contacts = Collections.unmodifiableList(new ArrayList<>(
                    Objects.requireNonNull(contacts, "contacts")));
            if (start.isAfter(peak) || peak.isAfter(end))
                throw new IllegalArgumentException("event times must satisfy start <= peak <= end");
        }
    }

    /** Immutable named contact within an eclipse. */
    public static final class Contact {
        public final String title;
        public final Instant time;
        public Contact(String title, Instant time) {
            this.title = Objects.requireNonNull(title, "title");
            this.time = Objects.requireNonNull(time, "time");
        }
    }

    /**
     * Searches events whose peak lies in the half-open interval {@code [start,end)}.
     * The requested interval may not exceed 366 days.
     */
    public static List<Event> search(Instant start, Instant end,
                                     double latitude, double longitude) {
        validate(start, end, latitude, longitude);
        checkCancelled();
        ArrayList<Event> events = new ArrayList<>();
        addMoonPhases(events, start, end);
        addLunarEclipses(events, start, end);
        addSolarEclipses(events, start, end, latitude, longitude);
        addCloseApproaches(events, start, end);
        events.sort(Comparator.comparing((Event e) -> e.peak).thenComparing(e -> e.id));
        return Collections.unmodifiableList(events);
    }

    private static void addMoonPhases(List<Event> out, Instant start, Instant end) {
        MoonQuarterInfo quarter = io.github.cosinekitty.astronomy.Astronomy.searchMoonQuarter(
                engineTime(start.minus(Duration.ofDays(8))));
        for (int guard = 0; guard < 60; guard++) {
            checkCancelled();
            Instant peak = instant(quarter.getTime());
            if (!peak.isBefore(start) && peak.isBefore(end)) {
                int phase = quarter.getQuarter();
                String[] names = {"新月", "上弦月", "满月", "下弦月"};
                Duration half = phase == 0 ? PHASE_HALF_WINDOW : EVENT_HALF_WINDOW;
                out.add(new Event("moon-phase-" + phase + "-" + peak.getEpochSecond(), names[phase],
                        "四个主要月相之一，时间为地心月相时刻。", SOURCE,
                        Kind.MOON_PHASE, peak, peak.minus(half), peak.plus(half), "moon", "",
                        false, false, phase, Double.NaN, Double.NaN, Double.NaN,
                        Collections.emptyList()));
            }
            if (!peak.isBefore(end)) break;
            quarter = io.github.cosinekitty.astronomy.Astronomy.nextMoonQuarter(quarter);
        }
    }

    private static void addLunarEclipses(List<Event> out, Instant start, Instant end) {
        LunarEclipseInfo eclipse = io.github.cosinekitty.astronomy.Astronomy.searchLunarEclipse(
                engineTime(start.minus(Duration.ofDays(40))));
        for (int guard = 0; guard < 8; guard++) {
            checkCancelled();
            Instant peak = instant(eclipse.getPeak());
            if (!peak.isBefore(start) && peak.isBefore(end)) out.add(lunarEvent(eclipse, peak));
            if (!peak.isBefore(end)) break;
            eclipse = io.github.cosinekitty.astronomy.Astronomy.nextLunarEclipse(eclipse.getPeak());
        }
    }

    private static Event lunarEvent(LunarEclipseInfo eclipse, Instant peak) {
        ArrayList<Contact> contacts = new ArrayList<>();
        addSymmetricContacts(contacts, peak, eclipse.getSdPenum(), "半影食始", "半影食终");
        addSymmetricContacts(contacts, peak, eclipse.getSdPartial(), "初亏", "复圆");
        addSymmetricContacts(contacts, peak, eclipse.getSdTotal(), "食既", "生光");
        contacts.add(new Contact("食甚", peak));
        contacts.sort(Comparator.comparing(c -> c.time));
        String type = eclipse.getKind() == EclipseKind.Total ? "月全食（红月）"
                : eclipse.getKind() == EclipseKind.Partial ? "月偏食" : "半影月食";
        return new Event("lunar-eclipse-" + peak.getEpochSecond(), type,
                type + "；接触时刻是全球地心食相，当地可见性需由规划器结合地平线判断。",
                SOURCE, Kind.LUNAR_ECLIPSE, peak, contacts.get(0).time,
                contacts.get(contacts.size() - 1).time, "moon", "sun", false, false, -1,
                Double.NaN, Double.NaN, Double.NaN, contacts);
    }

    private static void addSymmetricContacts(List<Contact> contacts, Instant peak,
                                             double minutes, String begin, String finish) {
        if (minutes <= 0.0) return;
        Duration offset = Duration.ofMillis(Math.round(minutes * 60_000.0));
        contacts.add(new Contact(begin, peak.minus(offset)));
        contacts.add(new Contact(finish, peak.plus(offset)));
    }

    private static void addSolarEclipses(List<Event> out, Instant start, Instant end,
                                         double latitude, double longitude) {
        Observer observer = new Observer(latitude, longitude, 0.0);
        GlobalSolarEclipseInfo global = io.github.cosinekitty.astronomy.Astronomy
                .searchGlobalSolarEclipse(engineTime(start.minus(Duration.ofDays(40))));
        for (int guard = 0; guard < 8; guard++) {
            checkCancelled();
            Instant globalPeak = instant(global.getPeak());
            // Local greatest eclipse can differ from the global peak across the query boundary.
            if (!globalPeak.isBefore(start.minus(Duration.ofDays(1))) && globalPeak.isBefore(end.plus(Duration.ofDays(1)))) {
                LocalSolarEclipseInfo local = io.github.cosinekitty.astronomy.Astronomy
                        .searchLocalSolarEclipse(global.getPeak().addDays(-1.0), observer);
                Instant localPeak = instant(local.getPeak().getTime());
                boolean present = Math.abs(Duration.between(globalPeak, localPeak).toHours()) < 24;
                Event event = solarEvent(global, present ? local : null, globalPeak);
                if (!event.peak.isBefore(start) && event.peak.isBefore(end)) out.add(event);
            }
            if (!globalPeak.isBefore(end.plus(Duration.ofDays(1)))) break;
            global = io.github.cosinekitty.astronomy.Astronomy.nextGlobalSolarEclipse(global.getPeak());
        }
    }

    private static Event solarEvent(GlobalSolarEclipseInfo global,
                                    LocalSolarEclipseInfo local, Instant globalPeak) {
        if (local == null) {
            String type = solarType(global.getKind());
            return new Event("solar-eclipse-" + globalPeak.getEpochSecond(), type + "（本地不可见）",
                    "此次日食在全球发生，但所选地点没有日食现象，不应推荐本地观测。", SOURCE,
                    Kind.SOLAR_ECLIPSE, globalPeak, globalPeak.minus(EVENT_HALF_WINDOW),
                    globalPeak.plus(EVENT_HALF_WINDOW), "sun", "moon", false, false, -1,
                    Double.NaN, Double.NaN, Double.NaN, Collections.emptyList());
        }
        ArrayList<Contact> contacts = new ArrayList<>();
        addContact(contacts, "偏食始", local.getPartialBegin());
        addContact(contacts, "全食或环食始", local.getTotalBegin());
        addContact(contacts, "食甚", local.getPeak());
        addContact(contacts, "全食或环食终", local.getTotalEnd());
        addContact(contacts, "偏食终", local.getPartialEnd());
        contacts.sort(Comparator.comparing(c -> c.time));
        Instant peak = instant(local.getPeak().getTime());
        String type = solarType(local.getKind());
        return new Event("solar-eclipse-" + globalPeak.getEpochSecond(), type,
                type + "；接触时刻按所选地点计算。观测太阳必须使用合格的太阳观测防护。", SOURCE,
                Kind.SOLAR_ECLIPSE, peak, contacts.get(0).time,
                contacts.get(contacts.size() - 1).time, "sun", "moon", false, true, -1,
                Double.NaN, Double.NaN, Double.NaN, contacts);
    }

    private static void addContact(List<Contact> contacts, String title, EclipseEvent event) {
        if (event != null) contacts.add(new Contact(title, instant(event.getTime())));
    }

    private static String solarType(EclipseKind kind) {
        if (kind == EclipseKind.Total) return "日全食";
        if (kind == EclipseKind.Annular) return "日环食";
        return "日偏食";
    }

    private static void addCloseApproaches(List<Event> out, Instant start, Instant end) {
        for (Body planet : PLANETS) scanPair(out, Body.Moon, planet, 8.0, start, end);
        for (int i = 0; i < PLANETS.length; i++)
            for (int j = i + 1; j < PLANETS.length; j++)
                scanPair(out, PLANETS[i], PLANETS[j], 5.0, start, end);
    }

    private static void scanPair(List<Event> out, Body a, Body b, double limit,
                                 Instant start, Instant end) {
        Instant scanStart = start.minus(COARSE_STEP);
        Instant scanEnd = end.plus(COARSE_STEP);
        Instant t0 = scanStart;
        Instant t1 = t0.plus(COARSE_STEP);
        double y0 = separation(a, b, t0), y1 = separation(a, b, t1);
        while (t1.isBefore(scanEnd)) {
            checkCancelled();
            Instant t2 = t1.plus(COARSE_STEP);
            if (t2.isAfter(scanEnd)) t2 = scanEnd;
            double y2 = separation(a, b, t2);
            if (y1 <= y0 && y1 < y2) {
                Instant peak = refineMinimum(a, b, t0, t2);
                double sep = separation(a, b, peak);
                if (!peak.isBefore(start) && peak.isBefore(end) && sep <= limit)
                    out.add(closeEvent(a, b, peak, sep));
            }
            t0 = t1; y0 = y1; t1 = t2; y1 = y2;
        }
    }

    private static Instant refineMinimum(Body a, Body b, Instant lo, Instant hi) {
        for (int i = 0; i < 28; i++) {
            checkCancelled();
            long nanos = Duration.between(lo, hi).toNanos();
            Instant left = lo.plusNanos(nanos / 3);
            Instant right = hi.minusNanos(nanos / 3);
            if (separation(a, b, left) <= separation(a, b, right)) hi = right;
            else lo = left;
        }
        return lo.plusNanos(Duration.between(lo, hi).toNanos() / 2);
    }

    private static double separation(Body a, Body b, Instant instant) {
        Time time = engineTime(instant);
        Vector va = io.github.cosinekitty.astronomy.Astronomy.geoVector(a, time, Aberration.Corrected);
        Vector vb = io.github.cosinekitty.astronomy.Astronomy.geoVector(b, time, Aberration.Corrected);
        double cosine = va.dot(vb) / (va.length() * vb.length());
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cosine))));
    }

    private static Event closeEvent(Body a, Body b, Instant peak, double separation) {
        String aid = bodyId(a), bid = bodyId(b);
        return new Event("close-approach-" + aid + "-" + bid + "-" + peak.getEpochSecond(),
                bodyName(a) + "与" + bodyName(b) + "视角最近",
                String.format(Locale.ROOT, "表观角距离极小值约 %.2f°；这是视角最近事件。", separation),
                SOURCE, Kind.CLOSE_APPROACH, peak, peak.minus(EVENT_HALF_WINDOW),
                peak.plus(EVENT_HALF_WINDOW), aid, bid, true, false, -1, separation,
                Double.NaN, Double.NaN, Collections.emptyList());
    }

    private static String bodyId(Body body) { return body.name().toLowerCase(Locale.ROOT); }

    private static String bodyName(Body body) {
        if (body == Body.Moon) return "月球";
        if (body == Body.Mercury) return "水星";
        if (body == Body.Venus) return "金星";
        if (body == Body.Mars) return "火星";
        if (body == Body.Jupiter) return "木星";
        if (body == Body.Saturn) return "土星";
        return body.name();
    }

    private static Time engineTime(Instant instant) {
        return Time.fromMillisecondsSince1970(instant.toEpochMilli());
    }

    private static Instant instant(Time time) {
        return Instant.ofEpochMilli(time.toMillisecondsSince1970());
    }

    private static void validate(Instant start, Instant end, double latitude, double longitude) {
        if (start == null || end == null) throw new IllegalArgumentException("start and end required");
        if (!start.isBefore(end)) throw new IllegalArgumentException("start must precede end");
        if (start.isBefore(MIN_TIME) || end.isAfter(MAX_TIME))
            throw new IllegalArgumentException("supported years are 1900 through 2100");
        if (Duration.between(start, end).compareTo(Duration.ofDays(366)) > 0)
            throw new IllegalArgumentException("range may not exceed 366 days");
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0)
            throw new IllegalArgumentException("latitude must be in [-90,+90]");
        if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0)
            throw new IllegalArgumentException("longitude must be in [-180,+180]");
    }

    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted())
            throw new CancellationException("celestial-event search interrupted");
    }
}
