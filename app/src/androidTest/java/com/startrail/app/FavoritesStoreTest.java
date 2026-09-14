package com.startrail.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.test.InstrumentationTestCase;

import java.util.Arrays;
import java.util.Collections;

/** Device-level persistence and boundary checks for favorites. */
public final class FavoritesStoreTest extends InstrumentationTestCase {
    private Context context;

    @Override protected void setUp() throws Exception {
        super.setUp();
        context = getInstrumentation().getTargetContext();
        preferences().edit().clear().commit();
    }

    @Override protected void tearDown() throws Exception {
        preferences().edit().clear().commit();
        super.tearDown();
    }

    public void testToggleSetAndPersistencePreserveInsertionOrder() {
        FavoritesStore store = new FavoritesStore(context);
        assertTrue(store.toggle("vega"));
        store.set("sirius", true);
        store.set("polaris", true);
        store.set("sirius", true);
        assertEquals(Arrays.asList("vega", "sirius", "polaris"), store.ids());
        assertTrue(store.contains("sirius"));

        assertFalse(store.toggle("sirius"));
        assertEquals(Arrays.asList("vega", "polaris"), store.ids());

        FavoritesStore reloaded = new FavoritesStore(context);
        assertEquals(Arrays.asList("vega", "polaris"), reloaded.ids());
        reloaded.set("vega", false);
        assertEquals(Collections.singletonList("polaris"), new FavoritesStore(context).ids());
    }

    public void testInvalidIdsAreRejected() {
        FavoritesStore store = new FavoritesStore(context);
        assertInvalid(() -> store.contains(null));
        assertInvalid(() -> store.toggle(""));
        assertInvalid(() -> store.toggle("   "));
        assertInvalid(() -> store.set(repeat('x', FavoritesStore.MAX_ID_LENGTH + 1), true));
        assertTrue(store.ids().isEmpty());
    }

    public void testMaximumFavoriteCountIsEnforcedWithoutLosingData() {
        FavoritesStore store = new FavoritesStore(context);
        for (int i = 0; i < FavoritesStore.MAX_FAVORITES; i++) store.set("star-" + i, true);
        try {
            store.set("one-too-many", true);
            fail("The favorite limit should be enforced");
        } catch (IllegalStateException expected) {
            assertEquals(FavoritesStore.MAX_FAVORITES, store.ids().size());
        }
    }

    public void testMalformedAndInvalidStoredDataIsHandledGracefully() {
        assertTrue(preferences().edit().putString("ids", "{not-json").commit());
        assertTrue(new FavoritesStore(context).ids().isEmpty());

        assertTrue(preferences().edit().putInt("ids", 7).commit());
        assertTrue(new FavoritesStore(context).ids().isEmpty());

        String oversized = repeat('z', FavoritesStore.MAX_ID_LENGTH + 1);
        String mixed = "[\"vega\",null,7,\"\",\"vega\",\"" + oversized + "\",\"sirius\"]";
        assertTrue(preferences().edit().putString("ids", mixed).commit());
        assertEquals(Arrays.asList("vega", "sirius"), new FavoritesStore(context).ids());
    }

    private SharedPreferences preferences() {
        return context.getSharedPreferences("favorites", Context.MODE_PRIVATE);
    }

    private void assertInvalid(Action action) {
        try {
            action.run();
            fail("Invalid ID should be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }

    private interface Action { void run(); }
}
