package com.startrail.app;
import android.app.Activity;
import android.app.AlertDialog;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Help and third-party attribution UI, independent from sky state. */
final class AppInfoDialog {
    private final Activity activity;
    private final boolean redMode;
    AppInfoDialog(Activity activity,boolean redMode){this.activity=activity;this.redMode=redMode;}
    void show(){
        ScrollView scroll=new ScrollView(activity);LinearLayout content=dialogColumn();scroll.addView(content);
        content.addView(text(getString(R.string.info_defaults_01),17,accent(),true));
        TextView copy=text(getString(R.string.info_defaults_02),14,ink(),false);copy.setPadding(0,dp(16),0,0);content.addView(copy);
        new AlertDialog.Builder(activity).setTitle(getString(R.string.info_defaults_03)).setView(scroll).setNeutralButton(getString(R.string.info_defaults_04),(d,w)->licenses()).setPositiveButton(getString(R.string.info_defaults_05),null).show();
    }
    private void licenses(){
        try(java.io.InputStream stream=activity.getAssets().open("third-party-notices.txt")){
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            byte[] buffer=new byte[4096];int count;
            while((count=stream.read(buffer))!=-1)bytes.write(buffer,0,count);
            ScrollView scroll=new ScrollView(activity);LinearLayout content=dialogColumn();scroll.addView(content);
            TextView copy=text(new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8),12,ink(),false);copy.setTextIsSelectable(true);content.addView(copy);
            new AlertDialog.Builder(activity).setTitle(getString(R.string.info_defaults_04)).setView(scroll).setPositiveButton(getString(R.string.close),null).show();
        }catch(java.io.IOException ex){toast(getString(R.string.info_licenses_01));}
    }

    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private int accent(){return AppPalette.accent(redMode);}
    private int ink(){return AppPalette.ink(redMode);}
    private LinearLayout dialogColumn(){LinearLayout l=new LinearLayout(activity);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(24),dp(12),dp(24),dp(12));return l;}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(activity);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);return t;}
    private String getString(int id,Object...args){return activity.getString(id,args);}
    private void toast(String value){android.widget.Toast.makeText(activity,value,android.widget.Toast.LENGTH_LONG).show();}
}
