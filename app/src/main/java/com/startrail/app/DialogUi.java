package com.startrail.app;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import com.startrail.app.astro.*;
import java.util.*;
import java.util.function.*;

/** Shared programmatic dialog widgets; no sky or persistence state. */
final class DialogUi {
    final Activity activity;
    private final BooleanSupplier redMode;
    DialogUi(Activity activity,BooleanSupplier redMode){this.activity=activity;this.redMode=redMode;}
    int dp(float n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    int panel(){return AppPalette.panel(redMode.getAsBoolean());}
    int line(){return AppPalette.line(redMode.getAsBoolean());}
    int ink(){return AppPalette.ink(redMode.getAsBoolean());}
    int muted(){return AppPalette.muted(redMode.getAsBoolean());}
    int accent(){return AppPalette.accent(redMode.getAsBoolean());}
    LinearLayout column(){LinearLayout l=new LinearLayout(activity);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(activity);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    LinearLayout dialogColumn(){LinearLayout l=column();l.setPadding(dp(24),dp(12),dp(24),dp(12));return l;}
    TextView text(String value,float size,int color,boolean bold){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    void title(LinearLayout parent,String value,int size){TextView t=text(value,size,ink(),true);t.setPadding(0,dp(12),0,dp(10));parent.addView(t);}
    TextView button(String label,Runnable action){TextView t=text(label,13,accent(),true);t.setGravity(Gravity.CENTER);t.setPadding(dp(12),dp(8),dp(12),dp(8));t.setMinHeight(dp(48));t.setBackground(selectable());t.setOnClickListener(v->action.run());t.setFocusable(true);return t;}
    android.graphics.drawable.Drawable selectable(){android.util.TypedValue v=new android.util.TypedValue();activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground,v,true);return activity.getDrawable(v.resourceId);}
    TextView pill(String label,Runnable action){TextView t=button(label,action);t.setBackground(round(panel(),14,line()));return t;}
    GradientDrawable round(int color,int radius,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),stroke);return d;}
    LinearLayout card(){LinearLayout l=column();l.setPadding(dp(18),dp(17),dp(18),dp(17));l.setBackground(round(panel(),20,line()));return l;}
    LinearLayout.LayoutParams spaced(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(12);return p;}
    EditText input(String hint){EditText e=new EditText(activity);e.setHint(hint);e.setTextColor(ink());e.setHintTextColor(muted());e.setTextSize(15);e.setSingleLine(true);e.setMinHeight(dp(52));return e;}
    CheckBox check(String title,boolean enabled){CheckBox c=new CheckBox(activity);c.setText(title);c.setTextColor(ink());c.setChecked(enabled);c.setMinHeight(dp(48));return c;}
}
