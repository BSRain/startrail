package com.startrail.app.astro;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Loader for the bundled J2000 faint-star backdrop catalogue. */
public final class BackgroundStars {
    private static final String HEADER = "ra_hours,dec_deg,mag,hip";

    private BackgroundStars() {}

    /**
     * Reads {@code background-stars.csv}. The caller retains ownership of {@code input}.
     *
     * @throws IOException if a row is malformed or contains an out-of-range value
     */
    public static List<Entry> load(InputStream input) throws IOException {
        if (input == null) throw new NullPointerException("input");

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8));
        List<Entry> entries = new ArrayList<>();
        boolean sawHeader = false;
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (!sawHeader) {
                if (!HEADER.equals(line)) {
                    throw new IOException("Unexpected background-star header at line " + lineNumber);
                }
                sawHeader = true;
                continue;
            }

            String[] fields = line.split(",", -1);
            if (fields.length != 4) {
                throw new IOException("Expected 4 fields at line " + lineNumber);
            }
            try {
                double ra = Double.parseDouble(fields[0]);
                double dec = Double.parseDouble(fields[1]);
                double magnitude = Double.parseDouble(fields[2]);
                int hip = fields[3].isEmpty() ? 0 : Integer.parseInt(fields[3]);
                if (!Double.isFinite(ra) || ra < 0.0 || ra >= 24.0
                        || !Double.isFinite(dec) || dec < -90.0 || dec > 90.0
                        || !Double.isFinite(magnitude) || hip < 0) {
                    throw new NumberFormatException("value outside catalogue range");
                }
                entries.add(new Entry(ra, dec, magnitude, hip));
            } catch (NumberFormatException exception) {
                throw new IOException("Invalid background star at line " + lineNumber, exception);
            }
        }
        if (!sawHeader) throw new IOException("Missing background-star header");
        return Collections.unmodifiableList(entries);
    }

    /** Deduplicates once per loaded catalogue. Passing an already prepared list is O(1). */
    public static List<Entry> forRendering(List<Entry> source) {
        if (source instanceof RenderList) return source;
        List<Entry> filtered = new ArrayList<>();
        List<Star> namedStars = StarCatalog.stars();
        for (Entry star : source) {
            boolean duplicate = false;
            for (Star named : namedStars) {
                double dra = Math.abs(star.raHours - named.raHours);
                dra = Math.min(dra, 24 - dra);
                if (dra * Math.cos(Math.toRadians(star.decDegrees)) * 15 < 0.03
                        && Math.abs(star.decDegrees - named.decDeg) < 0.03) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) filtered.add(star);
        }
        return new RenderList(filtered);
    }

    private static final class RenderList extends java.util.AbstractList<Entry>
            implements java.util.RandomAccess {
        private final List<Entry> entries;
        RenderList(List<Entry> entries) { this.entries = Collections.unmodifiableList(entries); }
        @Override public Entry get(int index) { return entries.get(index); }
        @Override public int size() { return entries.size(); }
    }

    public static final class Entry {
        public final double raHours;
        public final double decDegrees;
        public final double magnitude;
        /** Hipparcos identifier, or zero when the HYG row has no HIP identifier. */
        public final int hipId;

        private Entry(double raHours, double decDegrees, double magnitude, int hipId) {
            this.raHours = raHours;
            this.decDegrees = decDegrees;
            this.magnitude = magnitude;
            this.hipId = hipId;
        }
    }
}
