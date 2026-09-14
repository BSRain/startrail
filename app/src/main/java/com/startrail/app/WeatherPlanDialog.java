package com.startrail.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.*;
import com.startrail.app.astro.WeatherPlanner;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/** Personal, on-demand weather planning. All network and astronomy work runs off the UI thread. */
final class WeatherPlanDialog {
    static final int IMPORT_KEY = 4001;
    interface Actions {
        void preview(String id, Instant time);
        void add(String id);
        void locate();
    }
    private final Activity activity;
    private final Actions actions;
    private final QWeatherCredentials credentials;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Future<?> task;
    private volatile QWeatherClient activeClient;
    private int generation;
    private boolean destroyed;
    private AlertDialog dialog, configDialog;
    private LinearLayout results, options;
    private TextView optionToggle;
    private TextView times;
    private double lat, lon;
    private String place;
    private LocalDate day;
    private LocalTime from = LocalTime.of(19, 0), to = LocalTime.of(23, 0);
    private QWeatherClient.Forecast cached;
    private String cacheKey;
    private final DateTimeFormatter format;

    WeatherPlanDialog(Activity activity, Actions actions) {
        this.activity = activity;
        this.actions = actions;
        credentials = new QWeatherCredentials(activity);
        format = DateTimeFormatter.ofPattern(activity.getString(R.string.weather_date_time_pattern)).withZone(ZoneId.systemDefault());
    }

    void show(double latitude, double longitude, String location) {
        closePlan();
        lat = latitude; lon = longitude; place = location;
        day = LocalDate.now();
        if (LocalTime.now().isAfter(to)) day = day.plusDays(1);
        LinearLayout content = column();
        content.addView(label(String.format(Locale.CHINA, activity.getString(R.string.weather_location_coordinates), place, lat, lon), 14));
        content.addView(label(activity.getString(R.string.weather_real_date_timezone, ZoneId.systemDefault().getId()), 12));
        options = column();
        optionToggle = button(activity.getString(R.string.weather_collapse_time_settings), () -> {
            boolean visible=options.getVisibility()==android.view.View.VISIBLE;
            options.setVisibility(visible?android.view.View.GONE:android.view.View.VISIBLE);
            optionToggle.setText(visible ? R.string.weather_adjust_time_location : R.string.weather_collapse_time_settings);
        });
        content.addView(optionToggle); content.addView(options);
        options.addView(button(activity.getString(R.string.weather_update_phone_location), () -> { closePlan(); actions.locate(); }));
        LinearLayout days = new LinearLayout(activity);
        for (int i = 0; i < 3; i++) {
            final int offset = i;
            days.addView(button(new String[]{activity.getString(R.string.weather_today), activity.getString(R.string.weather_tomorrow), activity.getString(R.string.weather_day_after_tomorrow)}[i], () -> {
                day = LocalDate.now().plusDays(offset); updateTimes(); generate(false);
            }), new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        options.addView(days);
        times = label("", 16); options.addView(times); updateTimes();
        LinearLayout clocks = new LinearLayout(activity);
        clocks.addView(button(activity.getString(R.string.weather_start_time), () -> chooseTime(true)), new LinearLayout.LayoutParams(0, dp(48), 1));
        clocks.addView(button(activity.getString(R.string.weather_end_time), () -> chooseTime(false)), new LinearLayout.LayoutParams(0, dp(48), 1));
        options.addView(clocks);
        options.addView(label(activity.getString(R.string.weather_overnight_hint), 12));
        options.addView(button(activity.getString(R.string.weather_refresh_and_generate), () -> generate(true)));
        content.addView(button(activity.getString(R.string.weather_jwt_config_button), this::configure));
        results = column(); content.addView(results);
        dialog = showDialog(activity.getString(R.string.weather_plan_title), content);
        dialog.setOnDismissListener(d -> cancel());
        generate(false);
    }

    private void chooseTime(boolean start) {
        LocalTime value = start ? from : to;
        new TimePickerDialog(activity, (v, h, m) -> {
            if (start) from = LocalTime.of(h, m); else to = LocalTime.of(h, m);
            updateTimes(); generate(false);
        }, value.getHour(), value.getMinute(), true).show();
    }
    private void updateTimes() {
        times.setText(activity.getString(R.string.weather_time_range, day, from,
                !to.isAfter(from) ? activity.getString(R.string.weather_next_day_prefix) : "", to));
    }
    private void cancel() { generation++; if (task != null) task.cancel(true); QWeatherClient client=activeClient;if(client!=null)client.cancel(); }
    void pause() { cancel(); }
    void resume() { if(dialog!=null&&dialog.isShowing()&&(configDialog==null||!configDialog.isShowing()))generate(false); }
    private void closePlan() { cancel(); if (dialog != null) dialog.dismiss(); dialog = null; }
    void destroy() {
        destroyed = true; closePlan(); if (configDialog != null) configDialog.dismiss();
        worker.shutdownNow(); handler.removeCallbacksAndMessages(null);
    }

    private void generate(boolean force) {
        cancel();
        final int request = generation;
        if (dialog == null || !dialog.isShowing()) return;
        results.removeAllViews();
        Instant start = day.atTime(from).atZone(ZoneId.systemDefault()).toInstant();
        Instant end = (to.isAfter(from) ? day : day.plusDays(1)).atTime(to).atZone(ZoneId.systemDefault()).toInstant();
        if (start.isBefore(Instant.now())) start = Instant.now();
        if (!end.isAfter(start)) { results.addView(label(activity.getString(R.string.weather_period_ended), 15)); return; }
        final Instant begin = start, finish = end;
        final double latitude = lat, longitude = lon;
        results.addView(label(activity.getString(R.string.weather_generating), 15));
        task = worker.submit(() -> {
            QWeatherClient.Forecast forecast = null;
            String status = activity.getString(R.string.weather_private_key_missing);
            try {
                if (credentials.hasPrivateKey()) {
                    QWeatherCredentials.Config config = credentials.getConfig();
                    String key = config.host + ":" + latitude + ":" + longitude;
                    if (!force && cached != null && key.equals(cacheKey)
                            && !cached.fetchedAt.isAfter(Instant.now())
                            && Duration.between(cached.fetchedAt, Instant.now()).toMinutes() < 30) {
                        forecast = cached;
                    } else {
                        try {
                            QWeatherClient client=new QWeatherClient(); activeClient=client;
                            try { forecast = client.fetch(config.host, credentials.signToken(Instant.now()), latitude, longitude); }
                            finally { if(activeClient==client) activeClient=null; }
                            cached = forecast; cacheKey = key;
                        } catch (Exception e) {
                            if (Thread.currentThread().isInterrupted()) return;
                            status = activity.getString(R.string.weather_update_failed, safeFailure(e));
                        }
                    }
                }
                List<WeatherPlanner.Hour> hours = forecast == null ? Collections.emptyList() : forecast.hours;
                WeatherPlanner.Result plan = WeatherPlanner.plan(begin, finish, latitude, longitude, hours);
                final QWeatherClient.Forecast shownForecast = forecast;
                final String shownStatus = status;
                handler.post(() -> {
                    if (destroyed || generation != request || dialog == null || !dialog.isShowing()) return;
                    render(plan, shownForecast, shownStatus, begin, finish);
                });
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) return;
                handler.post(() -> {
                    if (destroyed || generation != request || dialog == null || !dialog.isShowing()) return;
                    results.removeAllViews(); results.addView(label(activity.getString(R.string.weather_plan_failed), 15));
                });
            }
        });
    }
    private String safeFailure(Exception e) {
        // Never render arbitrary exception messages: they may contain response bodies or credentials.
        if (e instanceof java.net.SocketTimeoutException) return activity.getString(R.string.weather_error_timeout);
        if(e instanceof java.io.IOException && e.getMessage()!=null) {
            switch(e.getMessage()) {
                case "Weather service returned HTTP 401": return activity.getString(R.string.weather_error_401);
                case "Weather service returned HTTP 403": return activity.getString(R.string.weather_error_403);
                case "Weather service returned HTTP 402": return activity.getString(R.string.weather_error_402);
                case "Weather service returned HTTP 404": return activity.getString(R.string.weather_error_404);
                case "Weather service returned HTTP 429": return activity.getString(R.string.weather_error_429);
            }
        }
        return activity.getString(R.string.weather_error_generic);
    }

    private void render(WeatherPlanner.Result plan, QWeatherClient.Forecast forecast, String status, Instant begin, Instant end) {
        results.removeAllViews();
        options.setVisibility(android.view.View.GONE); optionToggle.setText(R.string.weather_adjust_time_location);
        results.addView(label(activity.getString(R.string.weather_formatted_range, format.format(begin), format.format(end)), 16));
        if (forecast == null) results.addView(label(status, 14));
        else {
            results.addView(label(activity.getString(R.string.weather_fetched_at, format.format(forecast.fetchedAt)), 12));
            for (String attribution : forecast.attributions) {
                TextView source = label(attribution, 11); source.setTextIsSelectable(true); results.addView(source);
            }
            results.addView(button(activity.getString(R.string.weather_data_source), () -> {
                activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.qweather.com/")));
            }));
        }
        results.addView(label(activity.getString(R.string.weather_rules), 12));
        results.addView(label(plan.message, 14));
        for (WeatherPlanner.Pick pick : plan.picks) {
            LinearLayout card = column(); card.setBackground(background(0xff192b3d));
            card.addView(label(activity.getString(R.string.weather_pick_title, format.format(pick.bestTime), pick.displayName), 18));
            card.addView(label(String.format(Locale.CHINA, activity.getString(R.string.weather_pick_details), pick.altitudeDegrees, pick.azimuthDegrees, pick.reason, pick.equipment), 13));
            if(pick.weather != null) card.addView(label(weatherText(pick.weather),12));
            card.addView(button(activity.getString(R.string.weather_preview_time), () -> { closePlan(); actions.preview(pick.targetId, pick.bestTime); }));
            card.addView(button(activity.getString(R.string.weather_add_to_list), () -> actions.add(pick.targetId)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(10);
            results.addView(card, params);
        }
    }

    private String weatherText(WeatherPlanner.Hour h) {
        return activity.getString(R.string.weather_conditions, number(h.cloudPercent, activity.getString(R.string.weather_unit_percent)),
                number(h.precipMm, activity.getString(R.string.weather_unit_mm)),
                number(h.humidity, activity.getString(R.string.weather_unit_percent)),
                number(h.windKmh, activity.getString(R.string.weather_unit_kmh)));
    }
    private String number(double value,String unit) { return Double.isNaN(value) ? activity.getString(R.string.weather_unknown) : String.format(Locale.CHINA,"%.1f",value)+unit; }

    void configure() {
        if (configDialog != null) { configDialog.setOnDismissListener(null); configDialog.dismiss(); }
        LinearLayout content = column();
        content.addView(label(activity.getString(R.string.weather_privacy_notice), 13));
        QWeatherCredentials.Config config = credentials.getConfig();
        EditText host = input(content, activity.getString(R.string.weather_api_host), config.host);
        EditText developer = input(content, activity.getString(R.string.weather_developer_id), config.developerId);
        EditText project = input(content, activity.getString(R.string.weather_project_id), config.projectId);
        EditText credential = input(content, activity.getString(R.string.weather_credential_id), config.credentialId);
        content.addView(label(credentials.hasPrivateKey() ? activity.getString(R.string.weather_key_imported) : activity.getString(R.string.weather_key_not_imported), 14));
        content.addView(button(activity.getString(R.string.weather_save_config), () -> {
            try {
                credentials.saveConfig(new QWeatherCredentials.Config(host.getText().toString().trim(),
                        developer.getText().toString().trim(), project.getText().toString().trim(), credential.getText().toString().trim()));
                cached = null; toast(activity.getString(R.string.weather_config_saved));
            } catch (Exception e) { toast(activity.getString(R.string.weather_config_invalid)); }
        }));
        content.addView(button(activity.getString(R.string.weather_import_key), () -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
            activity.startActivityForResult(intent, IMPORT_KEY);
        }));
        content.addView(button(activity.getString(R.string.weather_delete_key), () -> {
            try { credentials.clearPrivateKey(); cached = null; cancel(); toast(activity.getString(R.string.weather_key_deleted)); configure(); }
            catch (Exception e) { toast(activity.getString(R.string.weather_delete_failed)); }
        }));
        content.addView(label(activity.getString(R.string.weather_permission_hint), 12));
        configDialog = showDialog(activity.getString(R.string.weather_jwt_title), content);
        configDialog.setOnDismissListener(d -> { if (!destroyed && dialog != null && dialog.isShowing()) generate(false); });
    }

    void importKey(Uri uri) {
        worker.submit(() -> {
            boolean success = false;
            byte[] pem = null;
            try (InputStream input = activity.getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (input == null) throw new java.io.IOException();
                byte[] buffer = new byte[1024]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > 16384) throw new java.io.IOException();
                    output.write(buffer, 0, count);
                }
                pem = output.toByteArray(); credentials.importPrivateKey(pem); success = true; cached = null;
            } catch (Exception ignored) { }
            finally { if (pem != null) Arrays.fill(pem, (byte) 0); }
            final boolean done = success;
            handler.post(() -> {
                if (destroyed) return;
                toast(done ? activity.getString(R.string.weather_key_import_success) : activity.getString(R.string.weather_key_import_failed));
                if (done) configure();
            });
        });
    }
    private EditText input(LinearLayout parent, String title, String value) {
        parent.addView(label(title, 12));
        EditText edit = new EditText(activity); edit.setSingleLine(true); edit.setTextColor(Color.WHITE);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setText(value); edit.setContentDescription(title); parent.addView(edit); return edit;
    }
    private AlertDialog showDialog(String title, LinearLayout content) {
        ScrollView scroll = new ScrollView(activity); scroll.addView(content);
        AlertDialog result = new AlertDialog.Builder(activity).setTitle(title).setView(scroll).setPositiveButton(R.string.weather_close, null).create();
        result.show(); result.getWindow().setBackgroundDrawable(background(0xff101f30)); return result;
    }
    private GradientDrawable background(int color) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(18)); return d; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(activity); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(14), dp(10), dp(14), dp(10)); return l; }
    private TextView label(String value, int size) { TextView t = new TextView(activity); t.setText(value); t.setTextSize(size); t.setTextColor(0xffc8dce7); t.setPadding(0, dp(5), 0, dp(5)); return t; }
    private TextView button(String value, Runnable action) { TextView t = label(value, 15); t.setTextColor(0xff9ee3d5); t.setGravity(android.view.Gravity.CENTER); t.setMinHeight(dp(48)); t.setOnClickListener(v -> action.run()); return t; }
    private void toast(String value) { Toast.makeText(activity, value, Toast.LENGTH_LONG).show(); }
    private int dp(int n) { return Math.round(n * activity.getResources().getDisplayMetrics().density); }
}
