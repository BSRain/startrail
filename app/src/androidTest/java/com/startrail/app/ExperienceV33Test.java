package com.startrail.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import com.startrail.app.astro.Star;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Regression coverage and visual fixtures for the 3.3 favorites and journal experience. */
@SuppressWarnings("deprecation")
public final class ExperienceV33Test extends ActivityInstrumentationTestCase2<MainActivity> {
    private Context context;
    private Map<String, ?> savedFavorites;
    private Map<String, ?> savedJournal;

    public ExperienceV33Test() { super(MainActivity.class); }

    @Override protected void setUp() throws Exception {
        super.setUp();
        context = getInstrumentation().getTargetContext();
        savedFavorites = copy(preferences("favorites").getAll());
        savedJournal = copy(preferences("journal").getAll());
        preferences("favorites").edit().clear().commit();
        preferences("journal").edit().clear().commit();
    }

    @Override protected void tearDown() throws Exception {
        restore(preferences("favorites"), savedFavorites);
        restore(preferences("journal"), savedJournal);
        super.tearDown();
    }

    public void testFavoriteDetailDialogListRemovalAndVisualFixtures() throws Throwable {
        MainActivity activity = getActivity();
        getInstrumentation().waitForIdleSync();
        final Star[] vega = new Star[1];

        runTestOnUiThread(() -> {
            try {
                SkyScene scene = (SkyScene) field(activity, "scene");
                vega[0] = scene.find("vega");
                assertNotNull(vega[0]);
                call(activity, "details", new Class<?>[]{Star.class}, vega[0]);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        getInstrumentation().waitForIdleSync();

        AccessibilityNodeInfo favorite = waitForText("☆ 收藏这个目标");
        assertNotNull("Favorite action must be present in details", favorite);
        assertTrue(favorite.performAction(AccessibilityNodeInfo.ACTION_CLICK));
        getInstrumentation().waitForIdleSync();
        assertTrue(((FavoritesStore) uncheckedField(activity, "favorites")).contains("vega"));
        assertNotNull(waitForText("★ 已收藏 · 点击取消"));
        AccessibilityNodeInfo detailClose = waitForText("关闭");
        assertNotNull(detailClose);
        assertTrue(detailClose.performAction(AccessibilityNodeInfo.ACTION_CLICK));
        getInstrumentation().waitForIdleSync();

        runTestOnUiThread(() -> {
            try {
                call(activity, "showFavorites", new Class<?>[0]);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        getInstrumentation().waitForIdleSync();
        assertNotNull("Favorite list must show the selected star", waitForText("织女一"));
        assertNotNull("Favorite list must offer removal", waitForText("移除"));
        capture(activity, "startrail-3.3-favorites.png");

        AccessibilityNodeInfo remove = waitForText("移除");
        assertNotNull(remove);
        assertTrue(remove.performAction(AccessibilityNodeInfo.ACTION_CLICK));
        getInstrumentation().waitForIdleSync();
        assertFalse(new FavoritesStore(context).contains("vega"));

        AccessibilityNodeInfo favoritesClose = waitForText("关闭");
        if (favoritesClose != null) favoritesClose.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(() -> {
            ObservationStore.Entry entry = new ObservationStore.Entry(
                    "vega", "织女星", "香港 · 测试", 1788962400000L,
                    22.3193, 114.1694, "夏夜里清晰可见，作为 3.3 手记页面的视觉样例。");
            ((ObservationStore) uncheckedField(activity, "journal")).save(entry);
            try {
                setField(activity, "tab", 2);
                call(activity, "renderPage", new Class<?>[0]);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(() -> {
            assertNotNull(exact(activity.getWindow().getDecorView(), "导出手记"));
            assertNotNull(exact(activity.getWindow().getDecorView(), "恢复手记"));
            assertNotNull(exact(activity.getWindow().getDecorView(), "织女星"));
        });
        capture(activity, "startrail-3.3-journal.png");
    }

    private SharedPreferences preferences(String name) {
        return context.getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    private static Object field(MainActivity activity, String name) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(activity);
    }

    private static Object uncheckedField(MainActivity activity, String name) {
        try {
            return field(activity, name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setField(MainActivity activity, String name, Object value) throws Exception {
        Field field = MainActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(activity, value);
    }

    private static void call(MainActivity activity, String name, Class<?>[] types, Object... args)
            throws Exception {
        Method method = MainActivity.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(activity, args);
    }

    private static TextView exact(View view, String expected) {
        if (view instanceof TextView && expected.contentEquals(((TextView) view).getText())) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = exact(group.getChildAt(i), expected);
                if (found != null) return found;
            }
        }
        return null;
    }

    private AccessibilityNodeInfo waitForText(String text) {
        long deadline = android.os.SystemClock.uptimeMillis() + 5000;
        do {
            AccessibilityNodeInfo root = getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                for (AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(text)) {
                    if (node.getText() != null && node.getText().toString().contains(text)) return node;
                }
            }
            android.os.SystemClock.sleep(50);
        } while (android.os.SystemClock.uptimeMillis() < deadline);
        return null;
    }

    private void capture(MainActivity activity, String name) throws Exception {
        getInstrumentation().waitForIdleSync();
        android.os.SystemClock.sleep(3800);
        Bitmap bitmap = getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        File output = new File(activity.getExternalFilesDir(null), name);
        try (FileOutputStream stream = new FileOutputStream(output)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } finally {
            bitmap.recycle();
        }
    }

    private static Map<String, ?> copy(Map<String, ?> source) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Set) value = new HashSet<>((Set<?>) value);
            result.put(entry.getKey(), value);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void restore(SharedPreferences preferences, Map<String, ?> values) {
        SharedPreferences.Editor editor = preferences.edit().clear();
        if (values != null) for (Map.Entry<String, ?> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String) editor.putString(entry.getKey(), (String) value);
            else if (value instanceof Boolean) editor.putBoolean(entry.getKey(), (Boolean) value);
            else if (value instanceof Float) editor.putFloat(entry.getKey(), (Float) value);
            else if (value instanceof Integer) editor.putInt(entry.getKey(), (Integer) value);
            else if (value instanceof Long) editor.putLong(entry.getKey(), (Long) value);
            else if (value instanceof Set) editor.putStringSet(
                    entry.getKey(), new HashSet<>((Set<String>) value));
        }
        editor.commit();
    }
}
