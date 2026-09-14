package com.startrail.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Persists the stable object IDs favorited by the user, in insertion order. */
final class FavoritesStore {
    static final int MAX_ID_LENGTH = 100;
    static final int MAX_FAVORITES = 200;

    private static final String PREFS_NAME = "favorites";
    private static final String IDS_KEY = "ids";

    private final SharedPreferences prefs;

    FavoritesStore(Context context) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    synchronized boolean contains(String id) {
        validateId(id);
        return read().contains(id);
    }

    synchronized boolean toggle(String id) {
        validateId(id);
        LinkedHashSet<String> ids = read();
        if (ids.remove(id)) {
            write(ids);
            return false;
        }
        add(ids, id);
        write(ids);
        return true;
    }

    synchronized List<String> ids() {
        return new ArrayList<>(read());
    }

    synchronized void set(String id, boolean favorite) {
        validateId(id);
        LinkedHashSet<String> ids = read();
        boolean changed;
        if (favorite) {
            if (ids.contains(id)) return;
            add(ids, id);
            changed = true;
        } else {
            changed = ids.remove(id);
        }
        if (changed) write(ids);
    }

    private static void validateId(String id) {
        if (id == null || id.trim().isEmpty() || id.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "favorite ID must contain 1 to " + MAX_ID_LENGTH + " characters");
        }
    }

    private static void add(Set<String> ids, String id) {
        if (ids.size() >= MAX_FAVORITES) {
            throw new IllegalStateException("at most " + MAX_FAVORITES + " favorites are allowed");
        }
        ids.add(id);
    }

    private LinkedHashSet<String> read() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        try {
            String stored = prefs.getString(IDS_KEY, "[]");
            JSONArray array = new JSONArray(stored == null ? "[]" : stored);
            for (int i = 0; i < array.length() && result.size() < MAX_FAVORITES; i++) {
                Object value = array.opt(i);
                if (value instanceof String) {
                    String id = (String) value;
                    if (!id.trim().isEmpty() && id.length() <= MAX_ID_LENGTH) result.add(id);
                }
            }
        } catch (JSONException | ClassCastException ignored) {
            // Corrupt or obsolete local state behaves like an empty favorites list.
        }
        return result;
    }

    private void write(Set<String> ids) {
        JSONArray array = new JSONArray();
        for (String id : ids) array.put(id);
        prefs.edit().putString(IDS_KEY, array.toString()).apply();
    }
}
