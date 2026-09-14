package com.startrail.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.test.InstrumentationTestCase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Device-level persistence checks for the independent observing list. */
public final class ObservingListStoreTest extends InstrumentationTestCase {
    private Context context;
    private Map<String, ?> savedPreferences;

    @Override protected void setUp() throws Exception {
        super.setUp();
        context = getInstrumentation().getTargetContext();
        savedPreferences = new HashMap<>(preferences().getAll());
        assertTrue(preferences().edit().clear().commit());
    }

    @Override protected void tearDown() throws Exception {
        SharedPreferences.Editor editor = preferences().edit().clear();
        for (Map.Entry<String, ?> entry : savedPreferences.entrySet()) {
            put(editor, entry.getKey(), entry.getValue());
        }
        assertTrue(editor.commit());
        super.tearDown();
    }

    public void testAddDuplicateRemoveAndOrder() {
        ObservingListStore store = new ObservingListStore(context);
        store.add("vega");
        store.add("sirius");
        store.add("vega");

        assertEntries(store.all(), new String[] {"vega", "sirius"}, new boolean[] {false, false});
        assertTrue(store.contains("vega"));
        store.remove("vega");
        store.remove("vega");
        assertEntries(store.all(), new String[] {"sirius"}, new boolean[] {false});
    }

    public void testRecreationPersistsEntriesAndCompletedState() {
        ObservingListStore store = new ObservingListStore(context);
        store.add("vega");
        store.add("polaris");
        store.setCompleted("vega", true);
        store.add("vega");

        ObservingListStore reloaded = new ObservingListStore(context);
        assertEntries(reloaded.all(), new String[] {"vega", "polaris"}, new boolean[] {true, false});
        reloaded.setCompleted("polaris", true);
        assertEntries(new ObservingListStore(context).all(),
                new String[] {"vega", "polaris"}, new boolean[] {true, true});
    }

    public void testMalformedDataThrowsAndIsNotOverwritten() {
        String malformed = "{not-json";
        assertTrue(preferences().edit().putString("entries", malformed).commit());
        ObservingListStore store = new ObservingListStore(context);

        assertCorrupt(() -> store.all());
        assertCorrupt(() -> store.add("vega"));
        assertEquals(malformed, preferences().getString("entries", null));
    }

    private void assertEntries(List<ObservingListStore.Entry> entries,
            String[] ids, boolean[] completed) {
        assertEquals(ids.length, entries.size());
        for (int i = 0; i < ids.length; i++) {
            assertEquals(ids[i], entries.get(i).starId);
            assertEquals(completed[i], entries.get(i).completed);
        }
    }

    private void assertCorrupt(Action action) {
        try {
            action.run();
            fail("Corrupt observing-list data should be reported");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("保留"));
        }
    }

    private SharedPreferences preferences() {
        return context.getSharedPreferences("observing_list", Context.MODE_PRIVATE);
    }

    @SuppressWarnings("unchecked")
    private static void put(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof String) editor.putString(key, (String) value);
        else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof java.util.Set) editor.putStringSet(key, (java.util.Set<String>) value);
    }

    private interface Action { void run(); }
}
