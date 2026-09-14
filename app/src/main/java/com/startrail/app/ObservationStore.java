package com.startrail.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Local-only observation journal. A whole entry is persisted atomically. */
final class ObservationStore {
    static final class Entry {
        String id, starId, target, place, note;
        long time;
        double latitude, longitude;
        Entry(String starId, String target, String place, long time, double lat, double lon, String note) {
            this.id = UUID.randomUUID().toString();
            this.starId = starId; this.target = target; this.place = place;
            this.time = time; this.latitude = lat; this.longitude = lon; this.note = note;
        }
    }
    private final SharedPreferences prefs;
    ObservationStore(Context context) { prefs = context.getSharedPreferences("journal", Context.MODE_PRIVATE); }
    List<Entry> all() {
        List<Entry> entries = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString("entries", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                Entry e = new Entry(o.optString("starId"), o.getString("target"), o.getString("place"),
                    o.getLong("time"), o.optDouble("latitude", 0), o.optDouble("longitude", 0), o.optString("note"));
                e.id = o.getString("id"); entries.add(e);
            }
        } catch (JSONException ex) {
            // Preserve a malformed journal for recovery instead of silently overwriting it.
            throw new IllegalStateException("观测记录无法读取，原始数据已保留。", ex);
        }
        entries.sort((a,b) -> Long.compare(b.time,a.time));
        return entries;
    }
    void save(Entry entry) {
        List<Entry> entries = all();
        entries.removeIf(e -> e.id.equals(entry.id)); entries.add(entry); write(entries);
    }
    void delete(String id) { List<Entry> entries = all(); entries.removeIf(e -> e.id.equals(id)); write(entries); }
    /** Adds missing IDs in one durable transaction; local versions always win conflicts. */
    int mergeImported(List<Entry> incoming) {
        List<Entry> validated=JournalBackup.decode(JournalBackup.encode(incoming));
        List<Entry> merged=all();java.util.Set<String> ids=new java.util.HashSet<>();
        for(Entry e:merged)ids.add(e.id);
        int added=0;for(Entry e:validated)if(ids.add(e.id)){merged.add(e);added++;}
        if(added==0)return 0;
        JournalBackup.encode(merged); // Validate the combined limits before touching storage.
        write(merged,true);return added;
    }
    private void write(List<Entry> entries) {
        write(entries,false);
    }
    @SuppressLint("ApplySharedPref") // Durable imports must report failure and synchronously restore the previous journal.
    private void write(List<Entry> entries,boolean durable) {
        JSONArray array = new JSONArray();
        try {
            for (Entry e: entries) {
                JSONObject o = new JSONObject();
                o.put("id",e.id).put("starId",e.starId).put("target",e.target).put("place",e.place)
                    .put("time",e.time).put("latitude",e.latitude).put("longitude",e.longitude).put("note",e.note);
                array.put(o);
            }
        } catch (JSONException ex) { throw new IllegalArgumentException("记录包含无效数据", ex); }
        if(durable){
            String previous=prefs.getString("entries","[]");
            if(!prefs.edit().putString("entries",array.toString()).commit()){
                prefs.edit().putString("entries",previous).commit();
                throw new IllegalStateException("备份未能写入本机，请检查可用存储空间后重试。");
            }
        }else prefs.edit().putString("entries", array.toString()).apply();
    }
}
