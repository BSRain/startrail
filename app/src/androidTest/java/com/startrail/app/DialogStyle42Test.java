package com.startrail.app;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.test.ActivityInstrumentationTestCase2;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.TextView;

/** Exercises both builder entry points and the framework controls they create. */
@SuppressWarnings("deprecation")
public final class DialogStyle42Test extends ActivityInstrumentationTestCase2<MainActivity> {
    private AlertDialog dialog;
    private SharedPreferences settings;
    private Boolean originalMode;

    public DialogStyle42Test() { super(MainActivity.class); }

    public void testCreateShowKeepsListenersAndFitsDisplay() throws Throwable {
        MainActivity activity = getActivity();
        settings = activity.getSharedPreferences("settings", 0);
        originalMode = settings.getBoolean("redMode", false);
        assertTrue(settings.edit().putBoolean("redMode", false).commit());
        final boolean[] clicked = {false}, dismissed = {false};
        runTestOnUiThread(() -> {
            dialog = new DialogStyle.Builder(activity).setTitle("Title").setMessage("Message")
                    .setPositiveButton("OK", (d, which) -> clicked[0] = true).create();
            dialog.setOnDismissListener(d -> dismissed[0] = true);
            dialog.show();
        });
        getInstrumentation().waitForIdleSync();
        runTestOnUiThread(() -> {
            assertChrome(activity, false);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        });
        getInstrumentation().waitForIdleSync();
        assertTrue(clicked[0]);
        assertTrue(dismissed[0]);
    }

    public void testBuilderShowUsesRedPalette() throws Throwable {
        MainActivity activity = getActivity();
        SharedPreferences settings = activity.getSharedPreferences("settings", 0);
        boolean original = settings.getBoolean("redMode", false);
        try {
            settings.edit().putBoolean("redMode", true).commit();
            runTestOnUiThread(() -> dialog = new DialogStyle.Builder(activity)
                    .setTitle("Title").setMessage("Message")
                    .setNegativeButton("Cancel", null).show());
            getInstrumentation().waitForIdleSync();
            runTestOnUiThread(() -> assertChrome(activity, true));
        } finally {
            if (dialog != null) runTestOnUiThread(() -> dialog.dismiss());
            settings.edit().putBoolean("redMode", original).commit();
        }
    }

    private void assertChrome(MainActivity activity, boolean redMode) {
        assertTrue(dialog.isShowing());
        assertTrue(dialog.getWindow().getDecorView().getBackground() instanceof GradientDrawable);
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int margin = Math.round(16 * metrics.density);
        int expected = Math.min(Math.round(560 * metrics.density),
                Math.max(1, metrics.widthPixels - 2 * margin));
        WindowManager.LayoutParams attributes = dialog.getWindow().getAttributes();
        assertEquals("width is bounded on narrow displays", expected, attributes.width);
        TextView message = dialog.findViewById(android.R.id.message);
        assertNotNull(message);
        assertEquals(AppPalette.ink(redMode), message.getCurrentTextColor());
        TextView button = dialog.getButton(redMode ? AlertDialog.BUTTON_NEGATIVE : AlertDialog.BUTTON_POSITIVE);
        assertNotNull(button);
        assertEquals(AppPalette.accent(redMode), button.getCurrentTextColor());
        int titleId = activity.getResources().getIdentifier("alertTitle", "id", "android");
        if (titleId != 0) {
            TextView title = dialog.findViewById(titleId);
            if (title != null) assertEquals(AppPalette.ink(redMode), title.getCurrentTextColor());
        }
    }

    @Override protected void tearDown() throws Exception {
        try {
            if (dialog != null) getInstrumentation().runOnMainSync(() -> dialog.dismiss());
        } finally {
            if (settings != null && originalMode != null)
                settings.edit().putBoolean("redMode", originalMode).commit();
            super.tearDown();
        }
    }
}
