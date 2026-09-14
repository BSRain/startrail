package com.startrail.app;

import com.startrail.app.astro.WeatherPlanner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.IDN;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/** Small, bounded client for QWeather's JWT-authenticated hourly weather API. */
public final class QWeatherClient {
    static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_HOURS = 240;
    private static final int TIMEOUT_MS = 10_000;
    private final Object requestLock = new Object();
    private volatile HttpURLConnection activeConnection;
    private volatile boolean canceled;
    private boolean used;

    public static final class Forecast {
        public final Instant fetchedAt;
        public final List<WeatherPlanner.Hour> hours;
        public final String tag;
        public final List<String> attributions;

        private Forecast(Instant fetchedAt, List<WeatherPlanner.Hour> hours,
                         String tag, List<String> attributions) {
            this.fetchedAt = fetchedAt;
            this.hours = Collections.unmodifiableList(new ArrayList<>(hours));
            this.tag = tag;
            this.attributions = Collections.unmodifiableList(new ArrayList<>(attributions));
        }
    }

    public Forecast fetch(String host, String token, double latitude, double longitude)
            throws IOException {
        synchronized (requestLock) {
            if (used) throw new IOException("Weather client is single-use");
            used = true;
        }
        checkCanceled();
        String safeHost = validateHost(host);
        if (token == null || token.trim().isEmpty()) throw new IOException("Missing weather credential");
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IOException("Invalid weather location");
        }
        checkCanceled();
        String path = String.format(Locale.US, "/weather/v1/hourly/%.2f/%.2f", latitude, longitude);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("https", safeHost, path
                    + "?hours=72&localTime=false&lang=zh").openConnection();
            synchronized (requestLock) {
                if (canceled || Thread.currentThread().isInterrupted()) {
                    connection.disconnect();
                    throw new IOException("Weather request interrupted");
                }
                activeConnection = connection;
            }
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            checkCanceled();
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Encoding", "gzip");
            checkCanceled();
            int status = connection.getResponseCode();
            checkCanceled();
            if (status < 200 || status >= 300) throw new IOException("Weather service returned HTTP " + status);
            String encoding = connection.getHeaderField("Content-Encoding");
            try (InputStream raw = connection.getInputStream()) {
                if ("gzip".equalsIgnoreCase(encoding)) {
                    try (InputStream input = new GZIPInputStream(raw)) {
                        return parse(new String(readBounded(input), StandardCharsets.UTF_8), Instant.now());
                    }
                }
                return parse(new String(readBounded(raw), StandardCharsets.UTF_8), Instant.now());
            }
        } catch (JSONException | DateTimeParseException e) {
            throw new IOException("Weather service returned invalid data", e);
        } finally {
            synchronized (requestLock) {
                if (activeConnection == connection) activeConnection = null;
            }
            if (connection != null) connection.disconnect();
        }
    }

    /** Permanently cancels this single-use client and closes its active HTTP connection. */
    public void cancel() {
        HttpURLConnection connection;
        synchronized (requestLock) {
            canceled = true;
            connection = activeConnection;
            activeConnection = null;
        }
        if (connection != null) connection.disconnect();
    }

    private void checkCanceled() throws IOException {
        if (canceled || Thread.currentThread().isInterrupted()) {
            throw new IOException("Weather request interrupted");
        }
    }

    public static Forecast parse(String json, Instant fetchedAt) throws JSONException {
        if (json == null || utf8LengthExceeds(json, MAX_RESPONSE_BYTES)) {
            throw new JSONException("Weather response exceeds limit");
        }
        if (fetchedAt == null) throw new JSONException("Missing retrieval time");
        JSONObject root = new JSONObject(json);
        JSONArray sourceHours = root.optJSONArray("hours");
        if (sourceHours == null || sourceHours.length() > MAX_HOURS) {
            throw new JSONException("Invalid hourly forecast count");
        }
        ArrayList<WeatherPlanner.Hour> hours = new ArrayList<>(sourceHours.length());
        Set<Instant> times = new HashSet<>();
        for (int i = 0; i < sourceHours.length(); i++) {
            JSONObject item = sourceHours.optJSONObject(i);
            if (item == null || !item.has("forecastTime")) throw new JSONException("Invalid hourly forecast");
            Instant time;
            try {
                time = OffsetDateTime.parse(item.getString("forecastTime")).toInstant();
            } catch (DateTimeParseException e) {
                throw new JSONException("Invalid forecast time");
            }
            if (!times.add(time)) throw new JSONException("Duplicate forecast time");
            double cloud = fractionPercent(item, "cloudCover");
            double humidity = fractionPercent(item, "humidity");
            double wind = measurement(item.optJSONObject("wind"), "speed", "wind");
            double precipitation = measurement(item.optJSONObject("precipitation"), "amount", "precipitation");
            try {
                hours.add(new WeatherPlanner.Hour(time, cloud, precipitation, humidity, wind));
            } catch (IllegalArgumentException e) {
                throw new JSONException("Invalid hourly forecast");
            }
        }

        String tag = null;
        ArrayList<String> attributions = new ArrayList<>();
        JSONObject metadata = root.optJSONObject("metadata");
        if (metadata != null) {
            if (metadata.has("tag") && !metadata.isNull("tag")) tag = metadata.getString("tag");
            JSONArray values = metadata.optJSONArray("attributions");
            if (values != null) {
                if(values.length()>32) throw new JSONException("Too many attributions");
                for (int i = 0; i < values.length(); i++) {
                    Object value = values.get(i);
                    if (!(value instanceof String) || ((String)value).length()>2048) throw new JSONException("Invalid attribution");
                    attributions.add((String) value);
                }
            }
        }
        return new Forecast(fetchedAt, hours, tag, attributions);
    }

    static String validateHost(String host) throws IOException {
        if (host == null) throw new IOException("Invalid weather host");
        final String ascii;
        try { ascii = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.US); }
        catch (IllegalArgumentException e) { throw new IOException("Invalid weather host"); }
        if (ascii.endsWith(".") || !ascii.endsWith(".qweatherapi.com")
                || ascii.length() <= ".qweatherapi.com".length()) {
            throw new IOException("Invalid weather host");
        }
        return ascii;
    }

    static byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        while (true) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Weather request interrupted");
            int count = input.read(buffer);
            if (count < 0) break;
            total += count;
            if (total > MAX_RESPONSE_BYTES) throw new IOException("Weather response exceeds limit");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static boolean utf8LengthExceeds(String text, int maximum) {
        long length = 0;
        for (int i = 0; i < text.length(); i++) {
            int c = text.charAt(i);
            if (c <= 0x7f) length++; else if (c <= 0x7ff) length += 2;
            else if (Character.isHighSurrogate((char) c) && i + 1 < text.length()
                    && Character.isLowSurrogate(text.charAt(i + 1))) { length += 4; i++; }
            else length += 3;
            if (length > maximum) return true;
        }
        return false;
    }

    private static double fractionPercent(JSONObject object, String key) throws JSONException {
        if (!object.has(key) || object.isNull(key)) return Double.NaN;
        double value = object.getDouble(key);
        if (!Double.isFinite(value) || value < 0 || value > 1) throw new JSONException("Invalid " + key);
        return value * 100.0;
    }

    private static double measurement(JSONObject parent, String child, String kind) throws JSONException {
        if (parent == null) return Double.NaN;
        JSONObject measurement = parent.optJSONObject(child);
        if (measurement == null || !measurement.has("value") || measurement.isNull("value")) return Double.NaN;
        double value = measurement.getDouble("value");
        if (!Double.isFinite(value) || value < 0) throw new JSONException("Invalid " + kind);
        String unit = measurement.optString("unit", "");
        if ("precipitation".equals(kind)) return "mm".equals(unit) ? value : Double.NaN;
        if ("m/s".equals(unit)) return value * 3.6;
        if ("km/h".equals(unit) || "kmh".equals(unit) || "kph".equals(unit)) return value;
        return Double.NaN;
    }
}
