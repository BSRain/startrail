package com.startrail.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Persists the user's ordered observing list independently from favorites and the journal. */
final class ObservingListStore {
    static final int MAX_ID_LENGTH = 100;
    static final int MAX_ENTRIES = 200;

    static final class Entry {
        final String starId;
        final boolean completed;

        Entry(String starId, boolean completed) {
            this.starId = starId;
            this.completed = completed;
        }
    }

    private static final String PREFS_NAME = "observing_list";
    private static final String ENTRIES_KEY = "entries";

    private final SharedPreferences prefs;

    ObservingListStore(Context context) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    synchronized List<Entry> all() {
        return new ArrayList<>(read());
    }

    synchronized boolean contains(String starId) {
        validateId(starId);
        return indexOf(read(), starId) >= 0;
    }

    synchronized void add(String starId) {
        validateId(starId);
        List<Entry> entries = read();
        if (indexOf(entries, starId) >= 0) return;
        if (entries.size() >= MAX_ENTRIES) {
            throw new IllegalStateException("观测清单最多保存 " + MAX_ENTRIES + " 个目标。");
        }
        entries.add(new Entry(starId, false));
        write(entries);
    }

    synchronized void remove(String starId) {
        validateId(starId);
        List<Entry> entries = read();
        int index = indexOf(entries, starId);
        if (index < 0) return;
        entries.remove(index);
        write(entries);
    }

    synchronized void setCompleted(String starId, boolean completed) {
        validateId(starId);
        List<Entry> entries = read();
        int index = indexOf(entries, starId);
        if (index < 0) return;
        Entry existing = entries.get(index);
        if (existing.completed == completed) return;
        entries.set(index, new Entry(existing.starId, completed));
        write(entries);
    }

    private List<Entry> read() {
        List<Entry> entries = new ArrayList<>();
        try {
            String stored = prefs.getString(ENTRIES_KEY, "[]");
            JSONArray array = new JSONArray(stored == null ? "[]" : stored);
            if (array.length() > MAX_ENTRIES) throw corrupt(null);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                String starId = object.getString("starId");
                validateId(starId);
                if (indexOf(entries, starId) >= 0) throw corrupt(null);
                entries.add(new Entry(starId, object.getBoolean("completed")));
            }
            return entries;
        } catch (JSONException | ClassCastException | IllegalArgumentException ex) {
            throw corrupt(ex);
        }
    }

    @SuppressLint("ApplySharedPref") // Saving is transactional: commit reports failure before the in-memory operation returns.
    private void write(List<Entry> entries) {
        JSONArray array = new JSONArray();
        try {
            for (Entry entry : entries) {
                JSONObject object = new JSONObject();
                object.put("starId", entry.starId);
                object.put("completed", entry.completed);
                array.put(object);
            }
        } catch (JSONException ex) {
            throw new IllegalArgumentException("observing-list entry cannot be encoded", ex);
        }

        String previous = prefs.getString(ENTRIES_KEY, "[]");
        if (!prefs.edit().putString(ENTRIES_KEY, array.toString()).commit()) {
            prefs.edit().putString(ENTRIES_KEY, previous).commit();
            throw new IllegalStateException("观测清单保存失败，已尝试恢复原始数据。");
        }
    }

    private static int indexOf(List<Entry> entries, String starId) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).starId.equals(starId)) return i;
        }
        return -1;
    }

    private static void validateId(String starId) {
        if (starId == null || starId.trim().isEmpty() || starId.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "star ID must contain 1 to " + MAX_ID_LENGTH + " characters");
        }
    }

    private static IllegalStateException corrupt(Throwable cause) {
        return new IllegalStateException("观测清单无法读取，原始数据已保留。", cause);
    }
}
