package com.startrail.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import com.startrail.app.astro.TonightPlanner;
import com.startrail.app.astro.ObservingEvents;
import com.startrail.app.astro.TimeAxis;
import com.startrail.app.astro.DeepSkyCatalog;
import android.os.SystemClock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import android.widget.TextView;
import android.widget.Toast;
import com.startrail.app.astro.Astronomy;
import com.startrail.app.astro.Star;
import com.startrail.app.astro.StarCatalog;
import com.startrail.app.astro.BackgroundStars;
import com.startrail.app.astro.SolarSystem;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Offline, beginner-friendly stargazing app. No account or backend required. */
public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private DialogUi dialogUi;
    private WeatherPlanDialog weatherPlan;
    private ObservationStore journal;
    private FavoritesStore favorites;
    private ObservingListStore observingList;
    private final ExecutorService eventsExecutor=Executors.newSingleThreadExecutor();
    private Future<?> eventsTask;
    private AlertDialog eventsDialog;
    private int eventsGeneration;
    private JournalTransferController journalTransfer;
    private Toast activeToast;
    private List<Star> stars;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout root, nav, header;
    private View divider;
    private boolean immersive;
    private String guidanceId;
    private double headingCorrection;
    private AlertDialog calibrationDialog;
    private final ExecutorService plannerExecutor=Executors.newSingleThreadExecutor();
    private Future<?> plannerTask;
    private int plannerGeneration;
    private FrameLayout body;
    private TextView contextLabel;
    private SkyMapView sky;
    private SkyScene scene;
    private List<BackgroundStars.Entry> backgroundStars=new ArrayList<>();
    private List<BackgroundStars.Entry> renderBackgroundStars=new ArrayList<>();
    private OrientationController orientation;
    private boolean followPhone=true,resumed;
    private double chartElevation=35,chartFov=75;
    private TextView followButton,sensorLabel;
    private String directionStatus;
    private int tab;
    private boolean live = true, redMode, showLines = true, showLabels = true;
    private long fixedTime;
    private boolean showDeepSky=true;
    private float galaxyOpacity=.8f, artworkOpacity=.7f;
    private boolean showGalaxy=true,showArtwork=true,showGrid,timelineExpanded,timelineDragging;
    private int playbackDirection,playbackRate=600;
    private long playbackElapsed;
    // Astronomy snapshots update at most ~8 times/s; sensor drawing remains independent.
    static final long PLAYBACK_INTERVAL_MS=120;
    private SeekBar timelineSeek;
    private TextView timelineLabel,reverseButton,playButton,speedButton,nowButton;
    private View guidanceBanner;
    private TimeAxis.DayWindow timelineDay;
    private TextView[] timelineMarks;
    private final Runnable playbackTick = new Runnable() {
        @Override public void run() {
            if (!resumed || tab != 0 || playbackDirection == 0) return;
            long elapsed = SystemClock.elapsedRealtime();
            // Account for actual elapsed time so a delayed tick does not slow simulation time.
            fixedTime = TimeAxis.advance(fixedTime, Math.max(0, elapsed - playbackElapsed),
                    playbackRate, playbackDirection);
            playbackElapsed = elapsed;
            refreshSkyTime();
            if (fixedTime == TimeAxis.MIN_MILLIS || fixedTime == TimeAxis.MAX_MILLIS) {
                pausePlayback();
                toast(getString(R.string.main_run_01));
            } else {
                handler.postDelayed(this, PLAYBACK_INTERVAL_MS);
            }
        }
    };
    private double latitude = 39.9042, longitude = 116.4074;
    private String place;
    private Star selected;
    private float chartRotation;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private final Runnable ticker = new Runnable() {
        @Override public void run() { if(live){if(tab==0&&sky!=null)refreshSkyTime();else renderPage();} handler.postDelayed(this,60000); }
    };
    private final Runnable locationTimeout = () -> {
        stopLocation(); toast(getString(R.string.main_run_03));
    };
    private int bg(){return AppPalette.bg(redMode);}
    private int panel(){return AppPalette.panel(redMode);}
    private int line(){return AppPalette.line(redMode);}
    private int ink(){return AppPalette.ink(redMode);}
    private int muted(){return AppPalette.muted(redMode);}
    private int accent(){return AppPalette.accent(redMode);}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private Instant now(){return Instant.ofEpochMilli(live?System.currentTimeMillis():fixedTime);}
    private String stamp(long ms){return DateTimeFormatter.ofPattern("yyyy.MM.dd  HH:mm",Locale.CHINA).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(ms));}
    private String shortName(Star s){return s.chineseName==null||s.chineseName.isEmpty()?s.name:s.chineseName;}
    private Astronomy.HorizontalPosition position(Star s){Astronomy.HorizontalPosition p=scene==null?null:scene.positions.get(s.id);return p!=null?p:Astronomy.horizontal(s,now(),latitude,longitude);}

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        directionStatus=getString(R.string.main_defaults_01);
        place=getString(R.string.main_run_02);
        dialogUi=new DialogUi(this,()->redMode);
        prefs=getSharedPreferences("settings",MODE_PRIVATE); journal=new ObservationStore(this);favorites=new FavoritesStore(this);observingList=new ObservingListStore(this);
        journalTransfer=new JournalTransferController(this,journal,this::pausePlayback,()->{tab=2;immersive=false;renderPage();});
        latitude=Double.longBitsToDouble(prefs.getLong("latitude",Double.doubleToLongBits(latitude)));
        longitude=Double.longBitsToDouble(prefs.getLong("longitude",Double.doubleToLongBits(longitude)));
        place=prefs.getString("place",place);redMode=prefs.getBoolean("redMode",false);
        showLines=prefs.getBoolean("lines",true);showLabels=prefs.getBoolean("labels",true);
        galaxyOpacity=readOpacity("galaxyOpacity",.8f);artworkOpacity=readOpacity("artworkOpacity",.7f);
        showDeepSky=prefs.getBoolean("deepSky",true);showGalaxy=prefs.getBoolean("galaxy",true);showArtwork=prefs.getBoolean("artwork",true);showGrid=prefs.getBoolean("grid",false);
        scene=SkyScene.create(now(),latitude,longitude);stars=new ArrayList<>(scene.objects);
        try(InputStream input=getAssets().open("background-stars.csv")){backgroundStars=BackgroundStars.load(input);}catch(java.io.IOException ex){toast(getString(R.string.main_on_create_01));}
        renderBackgroundStars=BackgroundStars.forRendering(backgroundStars);
        followPhone=prefs.getBoolean("followPhone",true);
        orientation=new OrientationController(this,(right,up,forward,status)->{
            directionStatus=status;
            if(right==null&&status.contains(getString(R.string.main_on_create_02)))setFollowing(false);
            if(sky!=null&&followPhone){sky.setDirectionReliable(right!=null&&orientation.isDirectionReliable()&&calibrationDialog==null);sky.setSensorOrientation(right,up,forward,status);}
            if(sensorLabel!=null&&followPhone){String shown=status+(!live?getString(R.string.main_on_create_03):"");if(!shown.contentEquals(sensorLabel.getText()))sensorLabel.setText(shown);}
        });
        headingCorrection=prefs.getFloat("headingCorrection",0);
        if(!Double.isFinite(headingCorrection)||Math.abs(headingCorrection)>30){headingCorrection=0;prefs.edit().putFloat("headingCorrection",0).apply();}
        orientation.setHeadingCorrection(headingCorrection);
        orientation.setLocation(latitude,longitude);
        if(!orientation.isAvailable()){followPhone=false;directionStatus=getString(R.string.main_on_create_04);}
        if(savedInstanceState!=null){tab=savedInstanceState.getInt("tab");live=savedInstanceState.getBoolean("live",true);fixedTime=savedInstanceState.getLong("fixedTime");chartRotation=savedInstanceState.getFloat("chartRotation");
            chartElevation=savedInstanceState.getDouble("chartElevation",35);chartFov=savedInstanceState.getDouble("chartFov",75);
            timelineExpanded=savedInstanceState.getBoolean("timelineExpanded",false);playbackRate=savedInstanceState.getInt("playbackRate",600);immersive=savedInstanceState.getBoolean("immersive",false);guidanceId=savedInstanceState.getString("guidanceId");
            String id=savedInstanceState.getString("selected");for(Star s:stars)if(s.id.equals(id))selected=s;}
        buildShell();
    }
    @Override protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putBoolean("timelineExpanded",timelineExpanded);out.putInt("playbackRate",playbackRate);out.putInt("tab",tab);out.putBoolean("live",live);out.putLong("fixedTime",fixedTime);out.putFloat("chartRotation",sky!=null?sky.getChartRotation():chartRotation);out.putDouble("chartElevation",sky!=null?sky.getViewingAltitude():chartElevation);out.putDouble("chartFov",sky!=null?sky.getFieldOfView():chartFov);out.putBoolean("immersive",immersive);out.putString("guidanceId",guidanceId);if(selected!=null)out.putString("selected",selected.id);}
    @Override protected void onResume(){super.onResume();resumed=true;handler.removeCallbacks(ticker);handler.postDelayed(ticker,60000);if(body!=null)renderPage();if(weatherPlan!=null)weatherPlan.resume();}
    @Override protected void onPause(){if(weatherPlan!=null)weatherPlan.pause();pausePlayback();super.onPause();resumed=false;handler.removeCallbacks(ticker);stopLocation();if(orientation!=null)orientation.stop();}
    @Override protected void onDestroy(){if(weatherPlan!=null)weatherPlan.destroy();eventsGeneration++;if(eventsDialog!=null)eventsDialog.dismiss();if(eventsTask!=null)eventsTask.cancel(true);eventsExecutor.shutdownNow();if(activeToast!=null)activeToast.cancel();if(journalTransfer!=null)journalTransfer.destroy();if(calibrationDialog!=null)calibrationDialog.dismiss();plannerGeneration++;if(plannerTask!=null)plannerTask.cancel(true);plannerExecutor.shutdownNow();handler.removeCallbacksAndMessages(null);stopLocation();if(orientation!=null)orientation.stop();super.onDestroy();}

    private void buildShell(){
        getWindow().setStatusBarColor(bg());getWindow().setNavigationBarColor(bg());
        root=column();root.setBackgroundColor(bg());root.setFitsSystemWindows(true);
        header=row();header.setPadding(dp(18),dp(2),dp(10),dp(2));
        LinearLayout brand=row();TextView name=text(getString(R.string.app_name),22,ink(),true);brand.addView(name);
        TextView mark=text("  STARTRAIL",9,accent(),false);brand.addView(mark);
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(button(getString(R.string.search),this::search));header.addView(button(getString(R.string.settings),this::settings));root.addView(header);
        contextLabel=text("",11,muted(),false);contextLabel.setPadding(dp(18),0,dp(18),dp(6));root.addView(contextLabel);
        body=new FrameLayout(this);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        divider=new View(this);divider.setBackgroundColor(line());root.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        nav=row();nav.setPadding(dp(8),dp(5),dp(8),dp(5));root.addView(nav);
        setContentView(root);renderPage();
    }
    private void refreshSkyTime(){
        scene=SkyScene.create(now(),latitude,longitude);stars=new ArrayList<>(scene.objects);
        if(selected!=null)selected=scene.find(selected.id);
        contextLabel.setText(getString(R.string.ui_context,place,(live?getString(R.string.time_live):getString(R.string.time_simulated)),stamp(now().toEpochMilli())));
        sky.setPlaybackMotion(playbackDirection!=0);sky.setScene(scene,latitude,longitude);sky.setSelected(selected);sky.setGuidanceTarget(scene.find(guidanceId));
        if(sensorLabel!=null)sensorLabel.setText(getString(R.string.ui_status_suffix,(followPhone?directionStatus:getString(R.string.main_refresh_sky_time_03)),(!live?getString(R.string.main_on_create_03):"")));
        updateTimeline();
    }
    private void renderPage(){
        if(body==null)return;
        if(tab!=0)pausePlayback();
        timelineSeek=null;timelineLabel=null;reverseButton=null;playButton=null;speedButton=null;nowButton=null;guidanceBanner=null;
        plannerGeneration++;if(plannerTask!=null){plannerTask.cancel(true);plannerTask=null;}
        applyImmersive();
        scene=SkyScene.create(now(),latitude,longitude);stars=new ArrayList<>(scene.objects);if(selected!=null)selected=scene.find(selected.id);
        int scrollY=0;if(body.getChildCount()>0 && body.getChildAt(0) instanceof ScrollView)scrollY=body.getChildAt(0).getScrollY();
        contextLabel.setText(getString(R.string.ui_context,place,(live?getString(R.string.time_live):getString(R.string.time_simulated)),stamp(now().toEpochMilli())));
        if(sky!=null){chartRotation=sky.getChartRotation();chartElevation=sky.getViewingAltitude();chartFov=sky.getFieldOfView();}
        body.removeAllViews();sky=null;
        if(tab==0)renderSky();else if(tab==1)renderVisible();else renderJournal();
        nav.removeAllViews();String[] titles={"✧  "+getString(R.string.sky),"◎  "+getString(R.string.observe),"▤  "+getString(R.string.journal)};
        for(int i=0;i<titles.length;i++){final int target=i;TextView v=button(titles[i],()->{tab=target;body.removeAllViews();renderPage();});v.setTextColor(i==tab?accent():muted());
            if(i==tab)v.setBackground(round(panel(),14,line()));nav.addView(v,new LinearLayout.LayoutParams(0,dp(52),1));}
        if(body.getChildCount()>0 && body.getChildAt(0) instanceof ScrollView){ScrollView scroll=(ScrollView)body.getChildAt(0);int y=scrollY;scroll.post(()->scroll.scrollTo(0,y));}
        syncTracking();
    }
    private LinearLayout page(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        LinearLayout content=column();content.setPadding(dp(20),dp(10),dp(20),dp(22));scroll.addView(content);body.addView(scroll);return content;
    }
    private void renderSky(){
        LinearLayout content=column();content.setPadding(dp(immersive?0:8),0,dp(immersive?0:8),0);body.addView(content);
        if(!immersive){
            LinearLayout heading=row();TextView title=text(getString(R.string.main_render_sky_01),16,ink(),true);title.setPadding(dp(8),0,0,0);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            heading.addView(button(getString(R.string.main_render_sky_02),this::chooseLocation));heading.addView(button(getString(R.string.main_render_sky_03),this::chooseTime));content.addView(heading);
        }
        LinearLayout mode=row();followButton=button(followPhone?getString(R.string.main_render_sky_04):getString(R.string.main_render_sky_05),this::toggleTracking);mode.addView(followButton,new LinearLayout.LayoutParams(0,dp(44),1));
        mode.addView(button(getString(R.string.main_render_sky_06),this::calibrationHelp));mode.addView(button(immersive?getString(R.string.main_render_sky_07):getString(R.string.main_render_sky_08),()->{immersive=!immersive;renderPage();}));content.addView(mode);
        sensorLabel=text(getString(R.string.ui_status_suffix,(followPhone?directionStatus:getString(R.string.main_refresh_sky_time_03)),(!live?getString(R.string.main_on_create_03):"")),11,muted(),false);sensorLabel.setPadding(dp(12),0,dp(8),dp(5));sensorLabel.setMinHeight(dp(26));content.addView(sensorLabel);
        if(guidanceId!=null){
            Star target=scene.find(guidanceId);
            if(target!=null){LinearLayout guide=row();guide.setBackground(round(panel(),10,line()));TextView info=text(getString(R.string.main_render_sky_09)+shortName(target)+(live?getString(R.string.main_render_sky_10):getString(R.string.main_render_sky_11)),12,accent(),true);info.setPadding(dp(12),0,0,0);guide.addView(info,new LinearLayout.LayoutParams(0,-2,1));guide.addView(button(getString(R.string.main_render_sky_12),()->{guidanceId=null;renderPage();}));content.addView(guide);guidanceBanner=guide;}
        }
        sky=new SkyMapView(this,stars,StarCatalog.lines());sky.setLayers(showLines,showLabels);sky.setRedMode(redMode);sky.setSelected(selected);sky.setManualView(chartRotation,chartElevation,chartFov);
        sky.setDecorationOpacity(galaxyOpacity,artworkOpacity);sky.setDecorations(showGalaxy,showArtwork,showGrid);sky.setDeepSkyVisible(showDeepSky);
        sky.setBackgroundStars(renderBackgroundStars);sky.setScene(scene,latitude,longitude);sky.setGuidanceTarget(scene.find(guidanceId));sky.setListener(star->{selected=star;details(star);});sky.setOnManualModeRequested(()->setFollowing(false));sky.setTracking(followPhone);sky.setDirectionReliable(orientation.isDirectionReliable()&&calibrationDialog==null);
        content.addView(sky,new LinearLayout.LayoutParams(-1,0,1));
        if(timelineExpanded)renderTimeline(content);
        LinearLayout timebar=row();
        nowButton=button(immersive?getString(R.string.search):live?getString(R.string.main_render_sky_13):getString(R.string.main_render_sky_14),()->{if(immersive)search();else returnToNow();});timebar.addView(nowButton,new LinearLayout.LayoutParams(0,dp(44),1));
        timebar.addView(button(timelineExpanded?getString(R.string.main_render_sky_15):getString(R.string.time_axis),()->{timelineExpanded=!timelineExpanded;renderPage();}),new LinearLayout.LayoutParams(0,dp(44),1));
        timebar.addView(button(getString(R.string.layers),this::settings),new LinearLayout.LayoutParams(0,dp(44),1));content.addView(timebar);
    }
    private void applyImmersive(){
        boolean full=immersive&&tab==0;
        if(header!=null)header.setVisibility(full?View.GONE:View.VISIBLE);
        if(contextLabel!=null)contextLabel.setVisibility(full?View.GONE:View.VISIBLE);
        if(nav!=null)nav.setVisibility(full?View.GONE:View.VISIBLE);
        if(divider!=null)divider.setVisibility(full?View.GONE:View.VISIBLE);
        getWindow().getDecorView().setSystemUiVisibility(full?View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY:View.SYSTEM_UI_FLAG_VISIBLE);
        if(full)getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    @Override public void onBackPressed(){if(immersive){immersive=false;renderPage();}else if(guidanceId!=null&&tab==0){guidanceId=null;renderPage();}else super.onBackPressed();}
    private void startGuidance(Star target){
        pausePlayback();
        guidanceId=target.id;selected=target;tab=0;
        // Real-world guidance always uses the present sky, even when launched from a simulation.
        live=true;setFollowing(true);renderPage();
        if(!followPhone)toast(getString(R.string.main_start_guidance_01));
        if(position(scene.find(target.id)).altitude<=0)toast(getString(R.string.main_start_guidance_02));
    }
    private void syncTracking(){if(orientation==null)return;if(resumed&&followPhone&&tab==0)orientation.start();else orientation.stop();}
    private void setFollowing(boolean enabled){followPhone=enabled&&orientation.isAvailable();prefs.edit().putBoolean("followPhone",followPhone).apply();if(sky!=null)sky.setTracking(followPhone);if(followButton!=null)followButton.setText(followPhone?getString(R.string.main_render_sky_04):getString(R.string.main_render_sky_05));if(sensorLabel!=null)sensorLabel.setText(followPhone?directionStatus:getString(R.string.main_set_following_01));syncTracking();}
    private void toggleTracking(){if(!orientation.isAvailable()){toast(getString(R.string.main_toggle_tracking_01));return;}setFollowing(!followPhone);}
    private void calibrationHelp(){
        pausePlayback();
        if(calibrationDialog!=null)return;
        if(sky!=null)sky.setDirectionReliable(false);
        LinearLayout content=dialogColumn();ScrollView scroll=new ScrollView(this);scroll.addView(content);
        content.addView(text(getString(R.string.main_calibration_help_01),13,ink(),false));
        content.addView(text(getString(R.string.main_calibration_help_02),13,muted(),false));
        final double original=headingCorrection;
        TextView value=text(String.format(Locale.CHINA,getString(R.string.main_calibration_help_03),original),16,accent(),true);value.setGravity(Gravity.CENTER);content.addView(value);
        SeekBar adjustment=new SeekBar(this);adjustment.setMax(60);adjustment.setProgress((int)Math.round(original)+30);adjustment.setContentDescription(getString(R.string.main_calibration_help_04));content.addView(adjustment,new LinearLayout.LayoutParams(-1,dp(48)));
        adjustment.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){value.setText(String.format(Locale.CHINA,getString(R.string.main_calibration_help_03),(double)progress-30));orientation.setHeadingCorrection(progress-30);}});
        LinearLayout changes=row();changes.addView(button("− 1°",()->adjustment.setProgress(adjustment.getProgress()-1)),new LinearLayout.LayoutParams(0,dp(44),1));changes.addView(button(getString(R.string.main_on_progress_changed_01),()->adjustment.setProgress(30)),new LinearLayout.LayoutParams(0,dp(44),1));changes.addView(button("+ 1°",()->adjustment.setProgress(adjustment.getProgress()+1)),new LinearLayout.LayoutParams(0,dp(44),1));content.addView(changes);
        content.addView(text(getString(R.string.main_on_progress_changed_02),12,muted(),false));
        AlertDialog dialog=new DialogStyle.Builder(this).setTitle(getString(R.string.main_on_progress_changed_03)).setView(scroll).setPositiveButton(getString(R.string.save),(d,w)->{headingCorrection=adjustment.getProgress()-30;prefs.edit().putFloat("headingCorrection",(float)headingCorrection).apply();orientation.setHeadingCorrection(headingCorrection);}).setNegativeButton(getString(R.string.cancel),null).create();
        calibrationDialog=dialog;
        dialog.setOnDismissListener(d->{orientation.setHeadingCorrection(headingCorrection);calibrationDialog=null;});dialog.show();if(dialog.getWindow()!=null){dialog.getWindow().setGravity(Gravity.BOTTOM);dialog.getWindow().setDimAmount(0.25f);}
    }
    private void shiftTime(long delta){pausePlayback();fixedTime=TimeAxis.clamp(now().toEpochMilli()+delta);live=false;guidanceId=null;renderPage();}
    private void shiftDay(int days){pausePlayback();fixedTime=TimeAxis.clamp(now().atZone(ZoneId.systemDefault()).plusDays(days).toInstant().toEpochMilli());live=false;guidanceId=null;renderPage();}
    private void pausePlayback(){
        boolean wasPlaying=playbackDirection!=0;
        if(wasPlaying&&sky!=null&&fixedTime!=TimeAxis.MIN_MILLIS&&fixedTime!=TimeAxis.MAX_MILLIS)
            fixedTime=TimeAxis.clamp(sky.displayedTimeMillis());
        playbackDirection=0;handler.removeCallbacks(playbackTick);
        if(sky!=null)sky.setPlaybackMotion(false);
        if(wasPlaying&&sky!=null)refreshSkyTime();else updateTimeline();
    }
    private void returnToNow(){pausePlayback();live=true;renderPage();}
    private void togglePlayback(int direction){
        if(playbackDirection==direction){pausePlayback();return;}
        if(playbackDirection!=0)pausePlayback();
        handler.removeCallbacks(playbackTick);
        fixedTime=TimeAxis.clamp(now().toEpochMilli());live=false;
        boolean hadGuide=guidanceId!=null;guidanceId=null;
        playbackDirection=direction;playbackElapsed=SystemClock.elapsedRealtime();
        if(hadGuide)renderPage();else refreshSkyTime();
        handler.postDelayed(playbackTick,PLAYBACK_INTERVAL_MS);
    }
    private void renderTimeline(LinearLayout content){
        LinearLayout panelView=column();panelView.setPadding(dp(8),dp(4),dp(8),0);panelView.setBackground(round(panel(),12,line()));
        LinearLayout caption=row();timelineLabel=text("",11,accent(),true);caption.addView(timelineLabel,new LinearLayout.LayoutParams(0,-2,1));caption.addView(button(getString(R.string.main_render_timeline_01),this::chooseTime));caption.addView(button(getString(R.string.main_render_timeline_02),this::returnToNow));panelView.addView(caption);
        timelineSeek=new SeekBar(this);timelineSeek.setContentDescription(getString(R.string.main_render_timeline_03));panelView.addView(timelineSeek,new LinearLayout.LayoutParams(-1,dp(36)));
        timelineDay=TimeAxis.day(now(),ZoneId.systemDefault());
        LinearLayout marks=row();timelineMarks=new TextView[5];for(int i=0;i<5;i++){timelineMarks[i]=text("",10,muted(),false);timelineMarks[i].setGravity(Gravity.CENTER);marks.addView(timelineMarks[i],new LinearLayout.LayoutParams(0,-2,1));}panelView.addView(marks);
        timelineSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar bar){pausePlayback();timelineDragging=true;}
            public void onStopTrackingTouch(SeekBar bar){timelineDragging=false;updateTimeline();}
            public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){if(!fromUser)return;fixedTime=timelineDay.atMinute(progress);live=false;guidanceId=null;if(guidanceBanner!=null)guidanceBanner.setVisibility(View.GONE);if(sky!=null)sky.setGuidanceTarget(null);refreshSkyTime();}
        });
        LinearLayout controls=row();
        controls.addView(button(getString(R.string.main_on_progress_changed_06),()->shiftDay(-1)),new LinearLayout.LayoutParams(0,dp(44),1));
        reverseButton=button("◀",()->togglePlayback(-1));reverseButton.setContentDescription(getString(R.string.main_on_progress_changed_07));controls.addView(reverseButton,new LinearLayout.LayoutParams(0,dp(44),1));
        speedButton=button("",()->{playbackRate=playbackRate==60?600:playbackRate==600?3600:60;playbackElapsed=SystemClock.elapsedRealtime();updateTimeline();});speedButton.setContentDescription(getString(R.string.main_on_progress_changed_08));controls.addView(speedButton,new LinearLayout.LayoutParams(0,dp(44),1.8f));
        playButton=button("▶",()->togglePlayback(1));playButton.setContentDescription(getString(R.string.main_on_progress_changed_09));controls.addView(playButton,new LinearLayout.LayoutParams(0,dp(44),1));
        controls.addView(button(getString(R.string.main_on_progress_changed_10),()->shiftDay(1)),new LinearLayout.LayoutParams(0,dp(44),1));panelView.addView(controls);content.addView(panelView);updateTimeline();
    }
    private void updateTimeline(){
        if(nowButton!=null&&!immersive)nowButton.setText(live?getString(R.string.main_render_sky_13):getString(R.string.main_render_sky_14));
        if(timelineLabel==null||timelineSeek==null)return;
        long millis=now().toEpochMilli();
        timelineLabel.setText(getString(R.string.ui_timeline,stamp(millis),ZoneId.systemDefault().getId(),(live?getString(R.string.time_live):playbackDirection==0?getString(R.string.main_update_timeline_01):playbackDirection>0?getString(R.string.main_update_timeline_02):getString(R.string.main_update_timeline_03))));
        if(!timelineDragging){timelineDay=TimeAxis.day(now(),ZoneId.systemDefault());timelineSeek.setMax(timelineDay.minutes);timelineSeek.setProgress(timelineDay.progress(millis));}
        if(timelineMarks!=null)for(int i=0;i<5;i++)timelineMarks[i].setText(i==4?getString(R.string.main_update_timeline_04):DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timelineDay.startMillis+(timelineDay.endMillis-timelineDay.startMillis)*i/4)));
        if(reverseButton!=null)reverseButton.setText(playbackDirection<0?"Ⅱ":"◀");
        if(playButton!=null)playButton.setText(playbackDirection>0?"Ⅱ":"▶");
        if(speedButton!=null)speedButton.setText(playbackRate==60?getString(R.string.main_update_timeline_05):playbackRate==600?getString(R.string.main_update_timeline_06):getString(R.string.main_update_timeline_07));
    }
    private List<Star> visibleStars(){
        List<Star> list=new ArrayList<>();
        for(Star s:stars)if(position(s).altitude>0)list.add(s);
        return list;
    }
    private Star recommended(List<Star> list){for(Star s:list)if(position(s).altitude>=20)return s;return list.isEmpty()?null:list.get(0);}
    private String direction(double az){String[] d={getString(R.string.main_direction_01),getString(R.string.main_direction_02),getString(R.string.main_direction_03),getString(R.string.main_direction_04),getString(R.string.main_direction_05),getString(R.string.main_direction_06),getString(R.string.main_direction_07),getString(R.string.main_direction_08)};return d[(int)Math.round(az/45.0)%8];}
    private String coordinates(Star s){Astronomy.HorizontalPosition h=position(s);return String.format(Locale.CHINA,getString(R.string.main_coordinates_01),direction(h.azimuth),h.azimuth,h.altitude,s.magnitude);}
    private View starCard(Star s){
        LinearLayout card=card();LinearLayout top=row();TextView name=text(shortName(s),20,ink(),true);top.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        Astronomy.HorizontalPosition h=position(s);TextView badge=text(h.altitude>=20?getString(R.string.main_star_card_01):h.altitude>0?getString(R.string.main_star_card_02):getString(R.string.main_star_card_03),10,h.altitude>0?accent():muted(),false);top.addView(badge);card.addView(top);
        TextView sub=text(s.name+"  /  "+s.constellation,12,muted(),false);sub.setPadding(0,dp(5),0,dp(12));card.addView(sub);
        card.addView(text(coordinates(s),12,accent(),false));card.setOnClickListener(v->{selected=s;details(s);});card.setContentDescription(shortName(s)+"，"+coordinates(s)+getString(R.string.main_star_card_04));return card;
    }
    private void showWeatherPlan(){
        pausePlayback();
        ensureWeatherPlan();
        weatherPlan.show(latitude,longitude,place);
    }
    private void ensureWeatherPlan(){
        if(weatherPlan==null)weatherPlan=new WeatherPlanDialog(this,new WeatherPlanDialog.Actions(){
            public void preview(String id,Instant time){previewTarget(id,time);}
            public void add(String id){addToObservingList(id);}
            public void locate(){MainActivity.this.locate();}
        });
    }
    private void renderVisible(){
        LinearLayout content=page();content.addView(text(getString(R.string.main_render_visible_01),12,accent(),false));title(content,getString(R.string.main_render_visible_02),28);
        content.addView(pill(getString(R.string.main_render_visible_03),this::showWeatherPlan),spaced());
        content.addView(pill(getString(R.string.main_render_visible_04)+favorites.ids().size()+getString(R.string.main_render_visible_05),this::showFavorites),spaced());
        content.addView(pill(getString(R.string.main_render_visible_06)+DeepSkyCatalog.entries().size()+getString(R.string.main_render_visible_05),this::showDeepSkyCatalog),spaced());
        content.addView(pill(getString(R.string.main_render_visible_07),this::showObservingList),spaced());
        content.addView(text(getString(R.string.main_render_visible_08),12,muted(),false));
        LinearLayout controls=row();controls.addView(button(getString(R.string.main_render_visible_09),this::chooseLocation),new LinearLayout.LayoutParams(0,dp(48),1));controls.addView(button(getString(R.string.main_render_visible_10),this::chooseTime),new LinearLayout.LayoutParams(0,dp(48),1));content.addView(controls);
        LinearLayout notice=card();notice.addView(text(getString(R.string.main_render_visible_11),13,accent(),true));
        TextView explanation=text(getString(R.string.main_render_visible_12),12,muted(),false);explanation.setPadding(0,dp(7),0,0);notice.addView(explanation);
        LinearLayout tonight=column();content.addView(tonight);loadTonight(tonight);content.addView(notice,spaced());
        List<Star> visible=visibleStars();title(content,getString(R.string.main_render_visible_13)+visible.size()+getString(R.string.main_render_visible_05),16);
        if(visible.isEmpty())content.addView(text(getString(R.string.main_render_visible_14),14,muted(),false));
        for(Star s:visible)content.addView(starCard(s),spaced());
    }
    private void loadTonight(LinearLayout container){
        title(container,getString(R.string.main_load_tonight_01),20);container.addView(text(getString(R.string.main_load_tonight_02),13,muted(),false));
        final int request=plannerGeneration;final Instant requestedTime=now();final double requestedLat=latitude,requestedLon=longitude;
        plannerTask=plannerExecutor.submit(()->{
            try{
                TonightPlanner.Result result=TonightPlanner.plan(requestedTime,requestedLat,requestedLon);
                if(Thread.currentThread().isInterrupted())return;
                handler.post(()->{
                    if(request!=plannerGeneration||isFinishing()||isDestroyed()||tab!=1)return;
                    container.removeAllViews();title(container,getString(R.string.main_load_tonight_01),20);
                    container.addView(text(result.message,13,accent(),false));
                    if(result.hasDarkWindow){
                        TextView window=text(stamp(result.windowStart.toEpochMilli())+" — "+stamp(result.windowEnd.toEpochMilli())+getString(R.string.main_load_tonight_03)+ZoneId.systemDefault().getId()+getString(R.string.main_load_tonight_04)+place,11,muted(),false);window.setPadding(0,dp(6),0,dp(6));container.addView(window);
                    }
                    for(TonightPlanner.Pick pick:result.picks){
                        LinearLayout card=card();LinearLayout heading=row();Star recommendedTarget=scene.find(pick.starId);heading.addView(text(recommendedTarget==null?pick.displayName:shortName(recommendedTarget),19,ink(),true),new LinearLayout.LayoutParams(0,-2,1));heading.addView(text(pick.kind.equals("moon")?getString(R.string.main_load_tonight_05):pick.kind.equals("planet")?getString(R.string.main_load_tonight_06):pick.kind.equals("deep_sky")?getString(R.string.main_load_tonight_07):getString(R.string.main_load_tonight_08),10,accent(),false));card.addView(heading);
                        TextView info=text(observingWindow(pick)+getString(R.string.main_load_tonight_09)+stamp(pick.bestTime.toEpochMilli())+String.format(Locale.CHINA,getString(R.string.main_load_tonight_10),pick.altitudeDegrees)+"\n"+pick.equipment+"\n"+pick.moonlightNote,12,muted(),false);info.setPadding(0,dp(8),0,dp(8));card.addView(info);
                        card.addView(text(getString(R.string.main_load_tonight_11),12,accent(),true));card.setOnClickListener(v->showRecommendation(pick));card.setContentDescription(pick.displayName+"，"+pick.reason+getString(R.string.main_load_tonight_12));container.addView(card,spaced());
                    }
                    TextView limits=text(getString(R.string.main_load_tonight_13)+result.sampleMinutes+getString(R.string.main_load_tonight_14),11,muted(),false);limits.setPadding(0,dp(12),0,dp(4));container.addView(limits);
                });
            }catch(java.util.concurrent.CancellationException ignored){}catch(Exception ex){
                handler.post(()->{if(request==plannerGeneration&&!isFinishing()&&!isDestroyed()){container.removeAllViews();container.addView(text(getString(R.string.main_load_tonight_15),13,muted(),false));}});
            }
        });
    }
    private String observingWindow(TonightPlanner.Pick pick){
        DateTimeFormatter format=DateTimeFormatter.ofPattern("MM.dd HH:mm",Locale.CHINA).withZone(ZoneId.systemDefault());
        return pick.windowStart.equals(pick.windowEnd)?getString(R.string.main_observing_window_01)+format.format(pick.windowStart):getString(R.string.main_observing_window_02)+format.format(pick.windowStart)+" — "+format.format(pick.windowEnd);
    }
    private void showRecommendation(TonightPlanner.Pick pick){
        final AlertDialog[] dialog={null};LinearLayout actions=dialogColumn();
        actions.addView(button(getString(R.string.main_show_recommendation_01),()->{Star target=scene.find(pick.starId);if(target!=null){dialog[0].dismiss();showObservingProfile(target);}}));
        actions.addView(button(getString(R.string.main_show_recommendation_02),()->addToObservingList(pick.starId)));
        dialog[0]=new DialogStyle.Builder(this).setTitle(pick.displayName+getString(R.string.main_show_recommendation_03))
            .setMessage(observingWindow(pick)+getString(R.string.main_show_recommendation_04)+stamp(pick.bestTime.toEpochMilli())+"\n"+pick.reason+"\n"+pick.equipment+"\n"+pick.moonlightNote+(pick.moonPhaseNote==null?"":"\n"+pick.moonPhaseNote)+getString(R.string.main_show_recommendation_05))
            .setView(actions)
            .setPositiveButton(getString(R.string.guide_now),(d,w)->{Star target=scene.find(pick.starId);if(target!=null)startGuidance(target);})
            .setNeutralButton(getString(R.string.main_show_recommendation_07),(d,w)->previewTarget(pick.starId,pick.bestTime))
            .setNegativeButton(getString(R.string.close),null).create();dialog[0].show();
    }
    private void previewTarget(String id,Instant time){
        pausePlayback();fixedTime=TimeAxis.clamp(time.toEpochMilli());live=false;guidanceId=null;
        setFollowing(false);tab=0;renderPage();Star target=scene.find(id);
        if(target!=null){selected=target;sky.focus(target);}
    }
    private void addToObservingList(String id){
        try{observingList.add(id);toast(getString(R.string.main_add_to_observing_list_01));}
        catch(IllegalArgumentException|IllegalStateException ex){toast(ex.getMessage());}
    }
    private void showObservingList(){
        pausePlayback();LinearLayout content=dialogColumn();
        content.addView(text(getString(R.string.main_show_observing_list_01),12,muted(),false));
        TextView count=text("",13,accent(),true);content.addView(count);
        ScrollView scroll=new ScrollView(this);LinearLayout list=column();scroll.addView(list);content.addView(scroll,new LinearLayout.LayoutParams(-1,dp(370)));
        AlertDialog dialog=new DialogStyle.Builder(this).setTitle(getString(R.string.main_show_observing_list_02)).setView(content).setNegativeButton(getString(R.string.close),null).create();
        Runnable fill=()->{
            list.removeAllViews();
            try{
                List<ObservingListStore.Entry> entries=observingList.all();int completed=0;for(ObservingListStore.Entry e:entries)if(e.completed)completed++;
                count.setText(getString(R.string.ui_completed,completed,entries.size()));scroll.getLayoutParams().height=dp(entries.isEmpty()?90:Math.min(370,entries.size()*195));
                if(entries.isEmpty())list.addView(text(getString(R.string.main_show_observing_list_03),14,muted(),false));
                for(ObservingListStore.Entry entry:entries){
                    Star target=scene.find(entry.starId);LinearLayout card=card();
                    CheckBox done=check(target==null?entry.starId:shortName(target),entry.completed);card.addView(done);
                    done.setOnClickListener(v->{try{observingList.setCompleted(entry.starId,done.isChecked());int n=0;for(ObservingListStore.Entry e:observingList.all())if(e.completed)n++;count.setText(getString(R.string.ui_completed,n,entries.size()));}catch(IllegalStateException ex){done.setChecked(!done.isChecked());toast(ex.getMessage());}});
                    if(target!=null){card.addView(text(coordinates(target),12,muted(),false));LinearLayout actions=row();
                        actions.addView(button(getString(R.string.main_show_observing_list_04),()->{dialog.dismiss();showObservingProfile(target);}),new LinearLayout.LayoutParams(0,-2,1));
                        actions.addView(button(getString(R.string.main_show_observing_list_05),()->{dialog.dismiss();details(target);}),new LinearLayout.LayoutParams(0,-2,1));card.addView(actions);}
                    TextView remove=button(getString(R.string.remove),()->{try{observingList.remove(entry.starId);dialog.dismiss();showObservingList();}catch(IllegalStateException ex){toast(ex.getMessage());}});card.addView(remove);list.addView(card,spaced());
                }
            }catch(IllegalStateException ex){count.setText(getString(R.string.main_show_observing_list_07));list.addView(text(ex.getMessage(),13,muted(),false));}
        };
        fill.run();dialog.show();content.setBackgroundColor(panel());if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(round(panel(),20,line()));
    }
    private String eventTime(Instant time){return time==null?getString(R.string.main_event_time_01):getString(R.string.main_event_time_02)+stamp(time.toEpochMilli());}
    private void showObservingProfile(Star target){
        pausePlayback();if(eventsDialog!=null)eventsDialog.dismiss();
        final int request=++eventsGeneration;final Instant start=now();final double lat=latitude,lon=longitude;final String observerPlace=place;
        LinearLayout content=dialogColumn();content.addView(text(observerPlace+String.format(Locale.CHINA,"  %.3f°, %.3f°",lat,lon),12,muted(),false));
        TextView status=text(getString(R.string.main_show_observing_profile_01),13,accent(),true);content.addView(status);
        LinearLayout resultArea=column();content.addView(resultArea);ScrollView scroll=new ScrollView(this);scroll.addView(content);
        final AlertDialog dialog=new DialogStyle.Builder(this).setTitle(shortName(target)+getString(R.string.main_show_observing_profile_02)).setView(scroll).setNegativeButton(getString(R.string.close),null).create();
        eventsDialog=dialog;dialog.setOnDismissListener(d->{if(eventsDialog==dialog){eventsGeneration++;if(eventsTask!=null)eventsTask.cancel(true);eventsDialog=null;}});dialog.show();content.setBackgroundColor(panel());if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(round(panel(),20,line()));
        eventsTask=eventsExecutor.submit(()->{
            try{
                ObservingEvents.Result result=ObservingEvents.calculate(target,start,lat,lon);
                handler.post(()->{
                    if(request!=eventsGeneration||isFinishing()||isDestroyed()||!dialog.isShowing())return;
                    status.setText(getString(R.string.ui_event_window,stamp(result.start.toEpochMilli()),stamp(result.end.toEpochMilli()),ZoneId.systemDefault().toString()));
                    resultArea.addView(text(getString(R.string.main_show_observing_profile_03)+eventTime(result.rise)+getString(R.string.main_show_observing_profile_04)+eventTime(result.transit)+(result.transit==null?"":String.format(Locale.CHINA,getString(R.string.main_show_observing_profile_05),result.transitAltitude))+getString(R.string.main_show_observing_profile_06)+eventTime(result.set),14,ink(),false));
                    resultArea.addView(text(getString(R.string.main_show_observing_profile_07),11,muted(),false));
                    AltitudeChartView chart=new AltitudeChartView(this,result,redMode);resultArea.addView(chart,new LinearLayout.LayoutParams(-1,dp(230)));
                    resultArea.addView(text(getString(R.string.main_show_observing_profile_08),11,muted(),false));
                    TextView selectedSample=text("",13,accent(),true);resultArea.addView(selectedSample);
                    SeekBar seek=new SeekBar(this);seek.setContentDescription(getString(R.string.main_show_observing_profile_09));seek.setMax(Math.max(0,result.samples.size()-1));resultArea.addView(seek);
                    final int[] selectedIndex={0};java.util.function.IntConsumer select=index->{selectedIndex[0]=index;chart.select(index);ObservingEvents.Sample sample=result.samples.get(index);selectedSample.setText(getString(R.string.ui_selected_sample,stamp(sample.time.toEpochMilli()),sample.altitude,(sample.altitude<0?getString(R.string.main_show_observing_profile_10):getString(R.string.main_show_observing_profile_11)),(sample.sunAltitude<=-18?getString(R.string.astronomical_darkness):sample.sunAltitude<=-6?getString(R.string.twilight):getString(R.string.main_show_observing_profile_14))));};
                    seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int value,boolean fromUser){select.accept(value);}public void onStartTrackingTouch(SeekBar bar){}public void onStopTrackingTouch(SeekBar bar){}});
                    chart.setListener(index->{seek.setProgress(index);select.accept(index);});select.accept(0);
                    resultArea.addView(button(getString(R.string.main_on_stop_tracking_touch_01),()->{Instant time=result.samples.get(selectedIndex[0]).time;dialog.dismiss();previewTarget(target.id,time);}));
                    resultArea.addView(button(getString(R.string.main_show_recommendation_02),()->addToObservingList(target.id)));
                });
            }catch(java.util.concurrent.CancellationException ignored){}catch(Exception ex){handler.post(()->{if(request==eventsGeneration&&dialog.isShowing())status.setText(getString(R.string.main_on_stop_tracking_touch_02));});}
        });
    }
    private void details(Star star){
        pausePlayback();
        selected=star;if(sky!=null)sky.setSelected(star);
        final AlertDialog[] detailDialog={null};
        LinearLayout content=dialogColumn();
        content.addView(button(getString(R.string.main_show_recommendation_01),()->{detailDialog[0].dismiss();showObservingProfile(star);}));
        content.addView(button(getString(R.string.main_show_recommendation_02),()->addToObservingList(star.id)));
        content.addView(text(star.name+"  /  "+star.constellation+(star.aliases.isEmpty()?"":getString(R.string.main_details_01)+String.join("、",star.aliases)),13,muted(),false));
        TextView coord=text(coordinates(star),15,accent(),true);coord.setPadding(0,dp(14),0,dp(12));content.addView(coord);
        content.addView(text(position(star).altitude>0?getString(R.string.main_details_02):getString(R.string.main_details_03),13,muted(),false));
        TextView description=text(star.description==null||star.description.isEmpty()?getString(R.string.main_details_04)+star.constellation+getString(R.string.main_details_05):star.description,14,ink(),false);description.setPadding(0,dp(18),0,dp(12));content.addView(description);
        SolarSystem.BodyPosition solar=scene.solar.get(star.id);
        DeepSkyCatalog.Entry deep=DeepSkyCatalog.find(star.id);
        if(deep!=null){
            content.addView(text((deep.star.id.equals("omega_cen")?"NGC 5139":deep.star.id.toUpperCase(Locale.ROOT))+" · "+deep.label+String.format(Locale.CHINA,getString(R.string.main_details_06),star.magnitude,deep.angularSizeArcmin),13,accent(),true));
            content.addView(text(deep.equipment+getString(R.string.main_details_07),13,muted(),false));
        }
        if(solar!=null){
            content.addView(text(String.format(Locale.CHINA,getString(R.string.main_details_08),solar.distanceKm,solar.illumination*100,solar.angularDiameterDegrees),12,muted(),false));
            if(star.id.equals("sun")){TextView warning=text(getString(R.string.main_details_09),13,accent(),true);warning.setPadding(0,dp(12),0,0);content.addView(warning);}
        }else content.addView(text(String.format(Locale.CHINA,getString(R.string.main_details_10),star.raHours,star.decDeg),11,muted(),false));
        content.addView(button(getString(R.string.main_details_11),()->editEntry(null,star)));
        TextView favorite=button(favorites.contains(star.id)?getString(R.string.main_details_12):getString(R.string.main_details_13),()->{});
        favorite.setOnClickListener(v->{try{boolean saved=favorites.toggle(star.id);favorite.setText(saved?getString(R.string.main_details_12):getString(R.string.main_details_13));toast(saved?getString(R.string.main_details_14):getString(R.string.main_details_15));}catch(IllegalArgumentException|IllegalStateException ex){toast(ex.getMessage());}});content.addView(favorite);
        ScrollView detailScroll=new ScrollView(this);detailScroll.addView(content);
        detailDialog[0]=new DialogStyle.Builder(this).setTitle(shortName(star)).setView(detailScroll)
            .setPositiveButton(star.id.equals("sun")?getString(R.string.main_details_16):getString(R.string.start_guidance),(d,w)->{if(star.id.equals("sun")){setFollowing(false);guidanceId=null;tab=0;renderPage();sky.focus(star);}else startGuidance(star);})
            .setNeutralButton(getString(R.string.main_details_18),(d,w)->{guidanceId=null;setFollowing(false);tab=0;body.removeAllViews();renderPage();if(sky!=null)sky.focus(star);if(position(star).altitude<=0)toast(getString(R.string.main_details_19));})
            .setNegativeButton(getString(R.string.close),null).show();
    }
    private void search(){
        pausePlayback();
        LinearLayout content=dialogColumn();EditText input=input(getString(R.string.main_search_01));content.addView(input);
        LinearLayout shortcuts=row();shortcuts.addView(button(getString(R.string.main_search_02),this::showFavorites),new LinearLayout.LayoutParams(0,-2,1));shortcuts.addView(button(getString(R.string.main_search_03),this::showDeepSkyCatalog),new LinearLayout.LayoutParams(0,-2,1));content.addView(shortcuts);
        TextView count=text("",12,muted(),false);content.addView(count);
        ScrollView scroll=new ScrollView(this);LinearLayout results=column();scroll.addView(results);content.addView(scroll,new LinearLayout.LayoutParams(-1,dp(320)));
        AlertDialog dialog=new DialogStyle.Builder(this).setTitle(getString(R.string.main_search_04)).setView(content).setNegativeButton(getString(R.string.close),null).create();
        Runnable filter=()->{
            results.removeAllViews();String q=input.getText().toString().trim().toLowerCase(Locale.ROOT);int matched=0;
            for(Star s:stars){String hay=(s.id+" "+s.name+" "+s.chineseName+" "+s.constellation+" "+String.join(" ",s.aliases)).toLowerCase(Locale.ROOT);
                if(!q.isEmpty()&&!hay.contains(q)&&!hay.replace(" ","").contains(q.replace(" ","")))continue;matched++;
                TextView item=button(shortName(s)+" · "+s.name+"\n"+s.constellation+"  /  "+(position(s).altitude>0?getString(R.string.main_search_05):getString(R.string.main_star_card_03)),()->{dialog.dismiss();details(s);});
                item.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);item.setTextSize(14);results.addView(item,new LinearLayout.LayoutParams(-1,dp(64)));
            }
            count.setText(matched==0?getString(R.string.main_search_06):getString(R.string.main_search_07)+matched+getString(R.string.main_search_08));
        };
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){filter.run();}public void afterTextChanged(Editable e){}});
        filter.run();dialog.show();
    }
    private void chooseTime(){
        pausePlayback();
        LinearLayout content=dialogColumn();content.addView(text(getString(R.string.main_choose_time_01)+stamp(now().toEpochMilli())+getString(R.string.main_choose_time_02)+ZoneId.systemDefault().getId()+getString(R.string.main_choose_time_03),13,muted(),false));
        new DialogStyle.Builder(this).setTitle(getString(R.string.main_choose_time_04)).setView(content).setPositiveButton(getString(R.string.main_choose_time_05),(d,w)->{
            Calendar calendar=Calendar.getInstance();calendar.setTimeInMillis(now().toEpochMilli());
            DatePickerDialog date=new DatePickerDialog(this,(picker,y,m,day)->{
                calendar.set(y,m,day);
                new TimePickerDialog(this,(tp,h,min)->{calendar.set(Calendar.HOUR_OF_DAY,h);calendar.set(Calendar.MINUTE,min);calendar.set(Calendar.SECOND,0);calendar.set(Calendar.MILLISECOND,0);fixedTime=TimeAxis.clamp(calendar.getTimeInMillis());live=false;guidanceId=null;renderPage();},calendar.get(Calendar.HOUR_OF_DAY),calendar.get(Calendar.MINUTE),true).show();
            },calendar.get(Calendar.YEAR),calendar.get(Calendar.MONTH),calendar.get(Calendar.DAY_OF_MONTH));
            Calendar lower=Calendar.getInstance();lower.clear();lower.set(1900,0,1);Calendar upper=Calendar.getInstance();upper.clear();upper.set(2100,11,31);
            date.getDatePicker().setMinDate(lower.getTimeInMillis());date.getDatePicker().setMaxDate(upper.getTimeInMillis());date.show();
        }).setNeutralButton(getString(R.string.main_choose_time_06),(d,w)->returnToNow()).setNegativeButton(getString(R.string.cancel),null).show();
    }
    private void chooseLocation(){
        pausePlayback();
        String[] names={getString(R.string.main_choose_location_01),getString(R.string.main_choose_location_02),getString(R.string.main_choose_location_03),getString(R.string.main_choose_location_04),getString(R.string.main_choose_location_05),getString(R.string.main_choose_location_06),getString(R.string.main_choose_location_07),getString(R.string.main_choose_location_08),getString(R.string.main_choose_location_09)};
        double[][] coords={{39.9042,116.4074},{31.2304,121.4737},{22.3193,114.1694},{29.6520,91.1721},{43.8256,87.6168},{-33.8688,151.2093},{51.5074,-0.1278}};
        new DialogStyle.Builder(this).setTitle(getString(R.string.main_choose_location_10)+place).setItems(names,(d,index)->{
            if(index==0){locate();return;}if(index==names.length-1){manualLocation();return;}
            stopLocation();setLocation(names[index]+getString(R.string.main_choose_location_11),coords[index-1][0],coords[index-1][1]);
        }).setNegativeButton(getString(R.string.cancel),null).show();
    }
    private void manualLocation(){
        LinearLayout content=dialogColumn();content.addView(text(getString(R.string.main_manual_location_01),12,muted(),false));
        EditText label=input(getString(R.string.main_manual_location_02));label.setText(getString(R.string.main_manual_location_03));content.addView(label);
        EditText lat=input(getString(R.string.main_manual_location_04)),lon=input(getString(R.string.main_manual_location_05));
        int type=InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED;lat.setInputType(type);lon.setInputType(type);
        lat.setText(String.format(Locale.US,"%.4f",latitude));lon.setText(String.format(Locale.US,"%.4f",longitude));content.addView(lat);content.addView(lon);
        AlertDialog dialog=new DialogStyle.Builder(this).setTitle(getString(R.string.main_manual_location_06)).setView(content).setPositiveButton(getString(R.string.apply),null).setNegativeButton(getString(R.string.cancel),null).create();dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{
            double a=Double.parseDouble(lat.getText().toString().trim()),o=Double.parseDouble(lon.getText().toString().trim());
            if(!Double.isFinite(a)||!Double.isFinite(o)||a< -90||a>90||o< -180||o>180)throw new NumberFormatException();
            String name=label.getText().toString().trim();stopLocation();setLocation((name.isEmpty()?getString(R.string.main_manual_location_03):name)+getString(R.string.main_choose_location_11),a,o);dialog.dismiss();
        }catch(NumberFormatException ex){toast(getString(R.string.main_manual_location_08));}});
    }
    private void setLocation(String label,double lat,double lon){
        place=label;latitude=lat;longitude=lon;
        if(orientation!=null)orientation.setLocation(latitude,longitude);
        prefs.edit().putString("place",place).putLong("latitude",Double.doubleToLongBits(lat)).putLong("longitude",Double.doubleToLongBits(lon)).apply();renderPage();
    }
    private boolean locationGranted(){return checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    private void locate(){
        if(!locationGranted()){requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION},42);return;}
        stopLocation();locationManager=(LocationManager)getSystemService(Context.LOCATION_SERVICE);
        if(locationManager==null){toast(getString(R.string.main_locate_01));return;}
        try{
            List<String> providers=locationManager.getProviders(true);Location best=null;
            for(String provider:providers){
                Location candidate;
                try{candidate=locationManager.getLastKnownLocation(provider);}catch(SecurityException ignored){continue;}
                if(candidate==null)continue;
                long ageNanos=android.os.SystemClock.elapsedRealtimeNanos()-candidate.getElapsedRealtimeNanos();
                if(ageNanos>=0 && ageNanos<300000000000L && (best==null||candidate.getAccuracy()<best.getAccuracy()))best=candidate;
            }
            if(best!=null){acceptLocation(best);return;}
            locationListener=new LocationListener(){public void onLocationChanged(Location location){acceptLocation(location);}public void onStatusChanged(String provider,int status,Bundle extras){}public void onProviderEnabled(String provider){}public void onProviderDisabled(String provider){}};
            int requested=0;
            for(String provider:providers){if(!provider.equals(LocationManager.GPS_PROVIDER)&&!provider.equals(LocationManager.NETWORK_PROVIDER))continue;try{locationManager.requestLocationUpdates(provider,1000,0,locationListener);requested++;}catch(SecurityException ignored){}}
            if(requested==0){stopLocation();toast(getString(R.string.main_on_provider_disabled_01));return;}
            toast(getString(R.string.main_on_provider_disabled_02));handler.postDelayed(locationTimeout,20000);
        }catch(SecurityException ex){stopLocation();toast(getString(R.string.main_on_provider_disabled_03));}
    }
    private void acceptLocation(Location location){stopLocation();setLocation(String.format(Locale.CHINA,getString(R.string.main_accept_location_01),location.getLatitude(),location.getLongitude()),location.getLatitude(),location.getLongitude());toast(getString(R.string.main_accept_location_02));}
    private void stopLocation(){handler.removeCallbacks(locationTimeout);if(locationManager!=null && locationListener!=null){try{locationManager.removeUpdates(locationListener);}catch(SecurityException ignored){}}locationListener=null;}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==42){if(locationGranted())locate();else toast(getString(R.string.main_on_request_permissions_result_01));}}

    private void renderJournal(){
        LinearLayout content=page();content.addView(text(getString(R.string.main_render_journal_01),12,accent(),false));title(content,getString(R.string.main_render_journal_02),28);
        content.addView(text(getString(R.string.main_render_journal_03),12,muted(),false));LinearLayout backup=row();backup.addView(button(getString(R.string.main_render_journal_04),()->chooseBackupDocument(true)),new LinearLayout.LayoutParams(0,dp(48),1));backup.addView(button(getString(R.string.main_render_journal_05),()->chooseBackupDocument(false)),new LinearLayout.LayoutParams(0,dp(48),1));content.addView(backup);content.addView(text(getString(R.string.main_render_journal_06),11,muted(),false));content.addView(pill(getString(R.string.main_render_journal_07),()->editEntry(null,null)),spaced());
        List<ObservationStore.Entry> entries;
        try{entries=journal.all();}catch(IllegalStateException ex){content.addView(text(ex.getMessage(),14,muted(),false));return;}
        if(entries.isEmpty()){
            LinearLayout empty=card();TextView star=text("✧",56,accent(),false);star.setGravity(Gravity.CENTER);empty.addView(star);
            TextView title=text(getString(R.string.main_render_journal_08),19,ink(),true);title.setGravity(Gravity.CENTER);empty.addView(title);
            TextView hint=text(getString(R.string.main_render_journal_09),13,muted(),false);hint.setPadding(0,dp(15),0,dp(20));hint.setGravity(Gravity.CENTER);empty.addView(hint);content.addView(empty,spaced());return;
        }
        title(content,entries.size()+getString(R.string.main_render_journal_10),15);
        for(ObservationStore.Entry entry:entries){LinearLayout card=card();card.addView(text(entry.target,19,ink(),true));
            TextView meta=text(stamp(entry.time)+"\n"+entry.place,11,accent(),false);meta.setPadding(0,dp(8),0,dp(12));card.addView(meta);
            TextView note=text(entry.note.isEmpty()?getString(R.string.main_render_journal_11):entry.note,14,muted(),false);note.setMaxLines(4);card.addView(note);
            card.setOnClickListener(v->viewEntry(entry));content.addView(card,spaced());}
    }
    private void viewEntry(ObservationStore.Entry entry){
        ScrollView scroll=new ScrollView(this);LinearLayout content=dialogColumn();scroll.addView(content);
        content.addView(text(stamp(entry.time)+"\n"+entry.place+String.format(Locale.CHINA,"\n%.4f°, %.4f°",entry.latitude,entry.longitude),12,accent(),false));
        TextView note=text(entry.note.isEmpty()?getString(R.string.main_view_entry_01):entry.note,15,ink(),false);note.setPadding(0,dp(16),0,0);content.addView(note);
        new DialogStyle.Builder(this).setTitle(entry.target).setView(scroll).setPositiveButton(getString(R.string.main_view_entry_02),(d,w)->editEntry(entry,null)).setNeutralButton(getString(R.string.main_view_entry_03),(d,w)->{
            new DialogStyle.Builder(this).setTitle(getString(R.string.main_view_entry_04)).setMessage(getString(R.string.main_view_entry_05)).setNegativeButton(getString(R.string.cancel),null).setPositiveButton(getString(R.string.main_view_entry_03),(x,y)->{try{journal.delete(entry.id);renderPage();}catch(IllegalStateException ex){toast(ex.getMessage());}}).show();
        }).setNegativeButton(getString(R.string.close),null).show();
    }
    private void editEntry(ObservationStore.Entry existing,Star star){
        // Capture the observing context when the editor opens, not when Save is pressed.
        long observedTime=existing==null?now().toEpochMilli():existing.time;
        double observedLat=existing==null?latitude:existing.latitude,observedLon=existing==null?longitude:existing.longitude;
        String observedPlace=existing==null?place:existing.place;
        LinearLayout content=dialogColumn();content.addView(text(stamp(observedTime)+"\n"+observedPlace+(existing==null&&!live?getString(R.string.main_edit_entry_01):""),12,muted(),false));
        EditText target=input(getString(R.string.main_edit_entry_02));target.setText(existing!=null?existing.target:star!=null?shortName(star):"");content.addView(target);
        EditText note=input(getString(R.string.main_edit_entry_03));note.setSingleLine(false);note.setMinLines(4);note.setMaxLines(8);note.setGravity(Gravity.TOP);note.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);if(existing!=null)note.setText(existing.note);content.addView(note);
        ScrollView editorScroll=new ScrollView(this);editorScroll.addView(content);
        AlertDialog dialog=new DialogStyle.Builder(this).setTitle(existing==null?getString(R.string.main_edit_entry_04):getString(R.string.main_edit_entry_05)).setView(editorScroll).setPositiveButton(getString(R.string.save),null).setNegativeButton(getString(R.string.cancel),null).create();dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String name=target.getText().toString().trim();if(name.isEmpty()){target.setError(getString(R.string.main_edit_entry_06));return;}if(name.length()>80){target.setError(getString(R.string.main_edit_entry_07));return;}if(note.length()>5000){note.setError(getString(R.string.main_edit_entry_08));return;}
            ObservationStore.Entry entry=new ObservationStore.Entry(existing!=null?existing.starId:star!=null?star.id:"",name,observedPlace,observedTime,observedLat,observedLon,note.getText().toString().trim());if(existing!=null)entry.id=existing.id;
            try{journal.save(entry);dialog.dismiss();toast(getString(R.string.main_edit_entry_09));renderPage();}catch(IllegalStateException ex){toast(ex.getMessage());}
        });
    }
    private CatalogDialogs catalogDialogs(){
        pausePlayback();
        return new CatalogDialogs(this,dialogUi,scene,favorites,live,stamp(now().toEpochMilli()),
                this::shortName,this::coordinates,this::details,()->{if(tab==1)renderPage();});
    }
    private void showDeepSkyCatalog(){catalogDialogs().showDeepSkyCatalog();}
    private void showFavorites(){catalogDialogs().showFavorites();}
    private void chooseBackupDocument(boolean export){pausePlayback();journalTransfer.chooseBackupDocument(export);}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==WeatherPlanDialog.IMPORT_KEY){
            if(resultCode==RESULT_OK&&data!=null&&data.getData()!=null){ensureWeatherPlan();weatherPlan.importKey(data.getData());}
        }else journalTransfer.onActivityResult(requestCode,resultCode,data);
    }
    private AlertDialog settings(){
        pausePlayback();
        LinearLayout content=dialogColumn();
        CheckBox deep=check(getString(R.string.main_settings_01),showDeepSky);content.addView(deep);
        CheckBox galaxy=check(getString(R.string.main_settings_02),showGalaxy),art=check(getString(R.string.main_settings_03),showArtwork),grid=check(getString(R.string.main_settings_04),showGrid);content.addView(galaxy);
        SeekBar galaxyStrength=opacitySlider(content,R.string.galaxy_opacity,galaxyOpacity);
        content.addView(art);
        SeekBar artworkStrength=opacitySlider(content,R.string.artwork_opacity,artworkOpacity);
        Runnable preview=()->{if(sky!=null)sky.setDecorationOpacity(galaxyStrength.getProgress()/100f,artworkStrength.getProgress()/100f);};
        bindOpacity(galaxyStrength,preview);bindOpacity(artworkStrength,preview);
        galaxyStrength.setEnabled(galaxy.isChecked());artworkStrength.setEnabled(art.isChecked());
        galaxy.setOnCheckedChangeListener((v,checked)->galaxyStrength.setEnabled(checked));
        art.setOnCheckedChangeListener((v,checked)->artworkStrength.setEnabled(checked));
        content.addView(grid);
        CheckBox lines=check(getString(R.string.main_settings_05),showLines),labels=check(getString(R.string.main_settings_06),showLabels),night=check(getString(R.string.main_settings_07),redMode);content.addView(lines);content.addView(labels);content.addView(night);
        content.addView(text(getString(R.string.main_settings_08)+place+String.format(Locale.CHINA,"  %.4f°, %.4f°",latitude,longitude)+getString(R.string.main_settings_09)+ZoneId.systemDefault().getId()+getString(R.string.main_settings_10)+DeepSkyCatalog.entries().size()+getString(R.string.main_settings_11)+backgroundStars.size()+getString(R.string.main_settings_12),12,muted(),false));
        content.addView(button(getString(R.string.main_settings_13),this::about));
        ScrollView settingsScroll=new ScrollView(this);settingsScroll.addView(content);
        AlertDialog settingsDialog=new DialogStyle.Builder(this).setTitle(getString(R.string.main_settings_14)).setView(settingsScroll).setPositiveButton(getString(R.string.apply),(d,w)->{galaxyOpacity=galaxyStrength.getProgress()/100f;artworkOpacity=artworkStrength.getProgress()/100f;showDeepSky=deep.isChecked();showGalaxy=galaxy.isChecked();showArtwork=art.isChecked();showGrid=grid.isChecked();showLines=lines.isChecked();showLabels=labels.isChecked();redMode=night.isChecked();prefs.edit().putFloat("galaxyOpacity",galaxyOpacity).putFloat("artworkOpacity",artworkOpacity).putBoolean("deepSky",showDeepSky).putBoolean("galaxy",showGalaxy).putBoolean("artwork",showArtwork).putBoolean("grid",showGrid).putBoolean("lines",showLines).putBoolean("labels",showLabels).putBoolean("redMode",redMode).apply();buildShell();}).setNegativeButton(getString(R.string.cancel),null).show();
        settingsDialog.setOnDismissListener(d->{if(sky!=null)sky.setDecorationOpacity(galaxyOpacity,artworkOpacity);});
        return settingsDialog;
    }
    private float readOpacity(String key,float fallback){
        float value=prefs.getFloat(key,fallback);
        return Float.isFinite(value)?Math.max(0f,Math.min(1f,value)):fallback;
    }
    private SeekBar opacitySlider(LinearLayout parent,int title,float value){
        TextView label=text(getString(R.string.opacity_percent,getString(title),Math.round(value*100)),13,muted(),false);
        parent.addView(label);
        SeekBar slider=new SeekBar(this);slider.setMax(100);slider.setProgress(Math.round(value*100));
        slider.setContentDescription(getString(title));slider.setMinimumHeight(dp(48));
        slider.setTag(new Object[]{label,title});parent.addView(slider);return slider;
    }
    private void bindOpacity(SeekBar slider,Runnable preview){
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean user){
                Object[] tag=(Object[])bar.getTag();
                ((TextView)tag[0]).setText(getString(R.string.opacity_percent,getString((Integer)tag[1]),progress));
                preview.run();
            }
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        });
    }
    private void about(){new AppInfoDialog(this,redMode).show();}
    private LinearLayout column(){return dialogUi.column();}
    private LinearLayout row(){return dialogUi.row();}
    private LinearLayout dialogColumn(){return dialogUi.dialogColumn();}
    private TextView text(String value,float size,int color,boolean bold){return dialogUi.text(value,size,color,bold);}
    private void title(LinearLayout parent,String value,int size){dialogUi.title(parent,value,size);}
    private TextView button(String label,Runnable action){return dialogUi.button(label,action);}
    private android.graphics.drawable.Drawable selectable(){return dialogUi.selectable();}
    private TextView pill(String label,Runnable action){return dialogUi.pill(label,action);}
    private GradientDrawable round(int color,int radius,int stroke){return dialogUi.round(color,radius,stroke);}
    private LinearLayout card(){return dialogUi.card();}
    private LinearLayout.LayoutParams spaced(){return dialogUi.spaced();}
    private EditText input(String hint){return dialogUi.input(hint);}
    private CheckBox check(String title,boolean enabled){return dialogUi.check(title,enabled);}
    private void toast(String message){if(activeToast!=null)activeToast.cancel();activeToast=Toast.makeText(this,message,Toast.LENGTH_LONG);activeToast.show();}
}
