package com.startrail.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

/** Consistent framework chrome for dialogs; custom dialog views keep their own styling. */
public final class DialogStyle {
    private DialogStyle() {}

    public static final class Builder extends AlertDialog.Builder {
        private final Context context;

        public Builder(Context context) {
            super(context);
            this.context = context;
        }

        @Override public AlertDialog create() {
            AlertDialog dialog = super.create();
            // A pre-draw hook also covers callers that use create().show(). It does not
            // consume the dialog's show, dismiss, or button click listeners.
            Window window = dialog.getWindow();
            if (window != null) {
                View decor = window.getDecorView();
                decor.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override public boolean onPreDraw() {
                        ViewTreeObserver observer = decor.getViewTreeObserver();
                        if (observer.isAlive()) observer.removeOnPreDrawListener(this);
                        apply(dialog, context);
                        return true;
                    }
                });
            }
            return dialog;
        }

        @Override public AlertDialog show() {
            AlertDialog dialog = super.show();
            apply(dialog, context);
            return dialog;
        }
    }

    @SuppressLint("DiscouragedApi") // Framework alertTitle has no public ID; resolved only when a dialog opens.
    public static void apply(AlertDialog dialog, Context context) {
        boolean redMode = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("redMode", false);
        Window window = dialog.getWindow();
        if (window != null) {
            DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            int margin = Math.round(16 * metrics.density);
            int maxWidth = Math.round(560 * metrics.density);
            int width = Math.min(maxWidth, Math.max(1, metrics.widthPixels - margin * 2));
            GradientDrawable background = new GradientDrawable();
            background.setColor(AppPalette.panel(redMode));
            background.setCornerRadius(20 * metrics.density);
            background.setStroke(Math.max(1, Math.round(metrics.density)), AppPalette.line(redMode));
            window.setBackgroundDrawable(background);
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        int titleId = context.getResources().getIdentifier("alertTitle", "id", "android");
        if (titleId != 0) colorText(dialog.findViewById(titleId), AppPalette.ink(redMode));
        colorText(dialog.findViewById(android.R.id.message), AppPalette.ink(redMode));
        colorButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE), AppPalette.accent(redMode));
        colorButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE), AppPalette.accent(redMode));
        colorButton(dialog.getButton(AlertDialog.BUTTON_NEUTRAL), AppPalette.accent(redMode));
    }

    private static void colorText(View view, int color) {
        if (view instanceof TextView) ((TextView) view).setTextColor(color);
    }

    private static void colorButton(Button button, int color) {
        if (button != null) button.setTextColor(color);
    }
}
