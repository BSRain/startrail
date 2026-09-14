package com.startrail.app;

import com.startrail.app.astro.TimeAxis;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Versioned, bounded JSON codec for offline journal backup files. */
final class JournalBackup {
    static final int SCHEMA_VERSION = 1;
    static final int MAX_BYTES = 2 * 1024 * 1024;
    /** Kept as a descriptive alias for callers concerned with decoded input. */
    static final int MAX_INPUT_BYTES = MAX_BYTES;
    static final int MAX_ENTRIES = 2000;

    private static final int MAX_ID = 36;
    private static final int MAX_STAR_ID = 128;
    private static final int MAX_TARGET = 80;
    private static final int MAX_PLACE = 256;
    private static final int MAX_NOTE = 5000;
    private static final int MAX_NESTING_DEPTH = 16;

    private JournalBackup() {}

    static String encode(List<ObservationStore.Entry> entries) {
        if (entries == null) throw invalid("entries");
        validateCount(entries.size());
        Set<String> ids = new HashSet<>();
        for (ObservationStore.Entry entry : entries) validateEntry(entry, ids);

        try {
            JSONArray array = new JSONArray();
            for (ObservationStore.Entry entry : entries) {
                array.put(new JSONObject()
                        .put("id", entry.id)
                        .put("starId", entry.starId)
                        .put("target", entry.target)
                        .put("place", entry.place)
                        .put("time", entry.time)
                        .put("latitude", entry.latitude)
                        .put("longitude", entry.longitude)
                        .put("note", entry.note));
            }
            String json = new JSONObject()
                    .put("app", "StarTrail")
                    .put("schemaVersion", SCHEMA_VERSION)
                    .put("entries", array)
                    .toString();
            if (utf8Length(json) > MAX_INPUT_BYTES) throw invalid("backup too large");
            return json;
        } catch (JSONException ex) {
            throw new IllegalArgumentException("Invalid journal entry", ex);
        }
    }

    static List<ObservationStore.Entry> decode(String json) {
        if (json == null || utf8Length(json) > MAX_INPUT_BYTES) throw invalid("backup too large");
        validateNesting(json);
        try {
            JSONTokener tokener = new JSONTokener(json);
            Object document = tokener.nextValue();
            if (!(document instanceof JSONObject) || tokener.nextClean() != 0)
                throw invalid("document");
            JSONObject root = (JSONObject) document;
            requireKeys(root, "app", "schemaVersion", "entries");
            if (!"StarTrail".equals(string(root, "app"))) throw invalid("app");
            Object schema = root.get("schemaVersion");
            if (!(schema instanceof Byte || schema instanceof Short || schema instanceof Integer
                    || schema instanceof Long) || ((Number) schema).longValue() != SCHEMA_VERSION)
                throw invalid("schemaVersion");
            Object entriesValue = root.get("entries");
            if (!(entriesValue instanceof JSONArray)) throw invalid("entries");
            JSONArray array = (JSONArray) entriesValue;
            validateCount(array.length());

            ArrayList<ObservationStore.Entry> result = new ArrayList<>(array.length());
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < array.length(); i++) {
                Object value = array.get(i);
                if (!(value instanceof JSONObject)) throw invalid("entry");
                JSONObject object = (JSONObject) value;
                requireKeys(object, "id", "starId", "target", "place", "time",
                        "latitude", "longitude", "note");
                String id = string(object, "id");
                String starId = string(object, "starId");
                String target = string(object, "target");
                String place = string(object, "place");
                String note = string(object, "note");
                long time = strictLong(object, "time");
                double latitude = strictDouble(object, "latitude");
                double longitude = strictDouble(object, "longitude");
                ObservationStore.Entry entry = new ObservationStore.Entry(
                        starId, target, place, time, latitude, longitude, note);
                entry.id = id;
                validateEntry(entry, ids);
                result.add(entry);
            }
            return result;
        } catch (JSONException ex) {
            throw new IllegalArgumentException("观测日志备份格式错误", ex);
        }
    }

    private static void validateEntry(ObservationStore.Entry entry, Set<String> ids) {
        if (entry == null) throw invalid("entry");
        bounded(entry.id, "id", 1, MAX_ID);
        try {
            if (!UUID.fromString(entry.id).toString().equals(entry.id)) throw invalid("id");
        } catch (IllegalArgumentException ex) {
            throw invalid("id");
        }
        if (!ids.add(entry.id)) throw invalid("duplicate id");
        bounded(entry.starId, "starId", 0, MAX_STAR_ID);
        bounded(entry.target, "target", 1, MAX_TARGET);
        if (entry.target.trim().isEmpty()) throw invalid("target");
        bounded(entry.place, "place", 0, MAX_PLACE);
        bounded(entry.note, "note", 0, MAX_NOTE);
        if (entry.time < TimeAxis.MIN_MILLIS || entry.time > TimeAxis.MAX_MILLIS)
            throw invalid("time");
        if (!Double.isFinite(entry.latitude) || entry.latitude < -90 || entry.latitude > 90)
            throw invalid("latitude");
        if (!Double.isFinite(entry.longitude) || entry.longitude < -180 || entry.longitude > 180)
            throw invalid("longitude");
    }

    private static void validateCount(int count) {
        if (count > MAX_ENTRIES) throw invalid("too many entries");
    }

    private static String string(JSONObject object, String key) throws JSONException {
        Object value = object.get(key);
        if (!(value instanceof String)) throw invalid(key);
        return (String) value;
    }

    private static long strictLong(JSONObject object, String key) throws JSONException {
        Object value = object.get(key);
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long)) throw invalid(key);
        return ((Number) value).longValue();
    }

    private static double strictDouble(JSONObject object, String key) throws JSONException {
        Object value = object.get(key);
        if (!(value instanceof Number)) throw invalid(key);
        double number = ((Number) value).doubleValue();
        if (!Double.isFinite(number)) throw invalid(key);
        return number;
    }

    private static void bounded(String value, String name, int min, int max) {
        if (value == null || value.length() < min || value.length() > max) throw invalid(name);
    }

    private static void requireKeys(JSONObject object, String... keys) throws JSONException {
        JSONArray names = object.names();
        if (names == null || names.length() != keys.length) throw invalid("fields");
        for (String key : keys) if (!object.has(key) || object.isNull(key)) throw invalid(key);
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    /** Stops pathological JSON from reaching org.json's recursive parser. */
    private static void validateNesting(String json) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') {
                quoted = true;
            } else if(c=='\''||c=='/'||c==0){
                throw invalid("JSON 语法");
            } else if (c == '{' || c == '[') {
                if (++depth > MAX_NESTING_DEPTH) throw invalid("nesting");
            } else if ((c == '}' || c == ']') && depth > 0) {
                depth--;
            }
        }
        if(quoted||depth!=0)throw invalid("JSON 结构");
    }

    private static IllegalArgumentException invalid(String field) {
        return new IllegalArgumentException("观测日志备份无效：" + field);
    }
}
