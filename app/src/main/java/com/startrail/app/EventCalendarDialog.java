package com.startrail.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.startrail.app.astro.CelestialEvents;
import com.startrail.app.astro.EventObservationPlanner;
import com.startrail.app.astro.MeteorShowers43;
import com.startrail.app.astro.WeatherPlanner;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Browses immutable celestial events and plans only the event selected by the user. */
final class EventCalendarDialog {
    interface Actions {
        void preview(String id, Instant time);
        void locate();
        void configureWeather();
    }

    private static final int[] RANGE_DAYS = {30, 90, 365};
    private static final int MAX_RENDERED_EVENTS = 80;

    private final Activity activity;
    private final Actions actions;
    private final DialogUi ui;
    private final QWeatherCredentials credentials;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final DateTimeFormatter dateTime;
    private AlertDialog dialog;
    private LinearLayout results;
    private Spinner filterSpinner, rangeSpinner;
    private Future<?> task;
    private volatile QWeatherClient activeClient;
    private int generation;
    private boolean destroyed;
    private double latitude, longitude;
    private String place;
    private List<CelestialEvents.Event> events = Collections.emptyList();
    private int visibleEventLimit = MAX_RENDERED_EVENTS;
    private QWeatherClient.Forecast cachedForecast;
    private String cachedLocation;

    EventCalendarDialog(Activity activity, Actions actions) {
        this.activity = activity;
        this.actions = actions;
        this.ui = new DialogUi(activity, () -> activity.getSharedPreferences("settings", 0)
                .getBoolean("redMode", false));
        this.credentials = new QWeatherCredentials(activity);
        this.dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z", Locale.getDefault())
                .withZone(ZoneId.systemDefault());
    }

    void show(double latitude, double longitude, String place) {
        dismiss();
        if (destroyed) return;
        this.latitude = latitude;
        this.longitude = longitude;
        this.place = place == null || place.trim().isEmpty() ? activity.getString(R.string.events_title) : place.trim();

        LinearLayout content = ui.dialogColumn();
        TextView location = ui.text(activity.getString(R.string.events_location, this.place, latitude, longitude), 14, ui.ink(), true);
        content.addView(location);
        TextView zone = ui.text(activity.getString(R.string.events_timezone, ZoneId.systemDefault().getId()), 12, ui.muted(), false);
        content.addView(zone);
        content.addView(ui.button(activity.getString(R.string.events_change_location), () -> {
            dismiss();
            actions.locate();
        }));

        ui.title(content, activity.getString(R.string.events_range_label), 14);
        rangeSpinner = spinner(R.array.events_ranges);
        content.addView(rangeSpinner);
        ui.title(content, activity.getString(R.string.events_filter_label), 14);
        filterSpinner = spinner(R.array.events_filters);
        content.addView(filterSpinner);
        results = ui.column();
        content.addView(results, ui.spaced());

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(content);
        dialog = new DialogStyle.Builder(activity).setTitle(R.string.events_title).setView(scroll)
                .setPositiveButton(R.string.events_close, null).create();
        dialog.setOnDismissListener(ignored -> cancel());
        dialog.show();

        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { reload(); }
            public void onNothingSelected(AdapterView<?> parent) { }
        };
        rangeSpinner.setOnItemSelectedListener(listener);
        filterSpinner.setOnItemSelectedListener(listener);
        reload();
    }

    void destroy() {
        destroyed = true;
        dismiss();
        worker.shutdownNow();
        handler.removeCallbacksAndMessages(null);
    }

    /** Stops in-flight astronomy or weather work while the activity is backgrounded. */
    void pause() { cancel(); }

    /** Rebuilds from real current time after a background transition invalidated prior work. */
    void resume() {
        if (showing()) reload();
    }

    private Spinner spinner(int entries) {
        Spinner spinner = new Spinner(activity);
        spinner.setMinimumHeight(ui.dp(48));
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(activity, entries,
                android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return spinner;
    }

    private void reload() {
        if (!showing()) return;
        cancel();
        final int request = generation;
        final int days = RANGE_DAYS[Math.max(0, Math.min(RANGE_DAYS.length - 1, rangeSpinner.getSelectedItemPosition()))];
        final int filter = filterSpinner.getSelectedItemPosition();
        final double lat = latitude, lon = longitude;
        results.removeAllViews();
        visibleEventLimit = MAX_RENDERED_EVENTS;
        results.addView(ui.text(activity.getString(R.string.events_loading), 14, ui.muted(), false));
        task = worker.submit(() -> {
            try {
                Instant start = Instant.now();
                Instant end = start.plus(Duration.ofDays(days));
                ArrayList<CelestialEvents.Event> found = new ArrayList<>(CelestialEvents.search(start, end, lat, lon));
                found.addAll(MeteorShowers43.search(start, end));
                found.removeIf(event -> !matches(event, filter));
                found.sort(Comparator.comparing(event -> event.peak));
                List<CelestialEvents.Event> immutable = Collections.unmodifiableList(new ArrayList<>(found));
                handler.post(() -> {
                    if (!valid(request)) return;
                    events = immutable;
                    renderList(days);
                });
            } catch (RuntimeException e) {
                handler.post(() -> {
                    if (!valid(request)) return;
                    results.removeAllViews();
                    results.addView(ui.text(activity.getString(R.string.events_load_failed), 14, ui.ink(), false));
                });
            }
        });
    }

    private boolean matches(CelestialEvents.Event event, int filter) {
        if (filter == 0) return true;
        if (filter == 1) return event.kind == CelestialEvents.Kind.SOLAR_ECLIPSE || event.kind == CelestialEvents.Kind.LUNAR_ECLIPSE;
        if (filter == 2) return event.kind == CelestialEvents.Kind.CLOSE_APPROACH;
        if (filter == 3) return event.kind == CelestialEvents.Kind.MOON_PHASE;
        return event.kind == CelestialEvents.Kind.METEOR_SHOWER;
    }

    private void renderList(int days) {
        results.removeAllViews();
        ZonedDateTime last = Instant.now().plus(Duration.ofDays(days)).atZone(ZoneId.systemDefault());
        if (last.getYear() < 2026 || last.getYear() > 2027) {
            results.addView(ui.text(activity.getString(R.string.events_outside_meteor_years), 12, ui.muted(), false));
        }
        if (events.isEmpty()) {
            results.addView(ui.text(activity.getString(R.string.events_empty), 14, ui.muted(), false));
            return;
        }
        int count = Math.min(visibleEventLimit, events.size());
        for (int i = 0; i < count; i++) {
            CelestialEvents.Event event = events.get(i);
            LinearLayout card = ui.card();
            card.addView(ui.text(event.title, 17, ui.ink(), true));
            String approximate = event.approximate ? activity.getString(R.string.events_approximate) : "";
            card.addView(ui.text(activity.getString(R.string.events_peak, dateTime.format(event.peak), approximate), 13, ui.muted(), false));
            card.addView(ui.text(visibilityText(event), 12, ui.muted(), false));
            card.addView(ui.button(activity.getString(R.string.events_show_detail), () -> openDetail(event)));
            results.addView(card, ui.spaced());
        }
        if (count < events.size()) results.addView(ui.button(
                activity.getString(R.string.events_load_more, count, events.size()), () -> {
                    visibleEventLimit += MAX_RENDERED_EVENTS;
                    renderCurrentList();
                }));
    }

    private void openDetail(CelestialEvents.Event event) {
        String key = String.format(Locale.US, "%.5f:%.5f", latitude, longitude);
        Instant now = Instant.now();
        boolean freshCache = cachedForecast != null && isFreshLocationCache(
                cachedForecast.fetchedAt, cachedLocation, key, now);
        if (automaticWeatherEligible(event, now) && freshCache) {
            showDetail(event, cachedForecast.hours,
                    activity.getString(R.string.events_weather_cached, dateTime.format(cachedForecast.fetchedAt)),
                    cachedForecast);
        } else if (shouldAutomaticallyRequestWeather(event, now,
                credentials.hasPrivateKey(), freshCache)) {
            requestWeather(event);
        } else {
            showDetail(event, Collections.emptyList(), null);
        }
    }

    static boolean automaticWeatherEligible(CelestialEvents.Event event, Instant now) {
        if (event == null || now == null || !event.end.isAfter(now)) return false;
        return event.start.isBefore(now.plus(Duration.ofHours(72)));
    }

    static boolean shouldAutomaticallyRequestWeather(CelestialEvents.Event event, Instant now,
                                                     boolean hasCredential, boolean hasFreshCache) {
        return hasCredential && !hasFreshCache && automaticWeatherEligible(event, now);
    }

    static boolean isFreshLocationCache(Instant fetchedAt, String cachedLocation,
                                        String requestedLocation, Instant now) {
        return fetchedAt != null && now != null && requestedLocation != null
                && requestedLocation.equals(cachedLocation) && !fetchedAt.isAfter(now)
                && Duration.between(fetchedAt, now).compareTo(Duration.ofMinutes(30)) < 0;
    }

    private void showDetail(CelestialEvents.Event event, List<WeatherPlanner.Hour> hours, String weatherStatus) {
        showDetail(event, hours, weatherStatus, null);
    }

    private void showDetail(CelestialEvents.Event event, List<WeatherPlanner.Hour> hours,
                            String weatherStatus, QWeatherClient.Forecast forecast) {
        if (!showing()) return;
        cancel();
        final int request = generation;
        results.removeAllViews();
        results.addView(ui.text(activity.getString(R.string.events_plan_loading), 14, ui.muted(), false));
        final double lat = latitude, lon = longitude;
        final Instant now = Instant.now();
        final List<WeatherPlanner.Hour> snapshot = hours == null ? Collections.emptyList() : new ArrayList<>(hours);
        task = worker.submit(() -> {
            try {
                EventObservationPlanner.Result plan = EventObservationPlanner.plan(event, now, lat, lon, snapshot);
                handler.post(() -> { if (valid(request)) renderDetail(event, plan, weatherStatus, forecast); });
            } catch (RuntimeException e) {
                handler.post(() -> {
                    if (!valid(request)) return;
                    results.removeAllViews();
                    results.addView(ui.text(activity.getString(R.string.events_plan_failed), 14, ui.ink(), false));
                    results.addView(ui.button(activity.getString(R.string.events_back), this::renderCurrentList));
                });
            }
        });
    }

    private void renderDetail(CelestialEvents.Event event, EventObservationPlanner.Result plan,
                              String weatherStatus, QWeatherClient.Forecast forecast) {
        results.removeAllViews();
        results.addView(ui.button(activity.getString(R.string.events_back), this::renderCurrentList));
        ui.title(results, event.title, 20);
        results.addView(ui.text(event.description, 14, ui.ink(), false));
        results.addView(ui.text(activity.getString(event.approximate
                ? R.string.events_estimated_peak : R.string.events_exact_peak,
                dateTime.format(event.peak)), 13, ui.muted(), false));
        results.addView(ui.text(visibilityText(event), 12, ui.muted(), false));
        if (event.kind == CelestialEvents.Kind.SOLAR_ECLIPSE)
            results.addView(ui.text(activity.getString(R.string.events_solar_warning), 14, ui.accent(), true));
        if (!plan.contacts.isEmpty()) {
            ui.title(results, activity.getString(R.string.events_contacts), 14);
            for (EventObservationPlanner.ContactVisibility contact : plan.contacts)
                results.addView(ui.text(activity.getString(R.string.events_contact_visibility,
                        contact.title, dateTime.format(contact.time), contact.altitude,
                        activity.getString(contact.aboveHorizon ? R.string.events_above_horizon : R.string.events_below_horizon)),
                        12, ui.muted(), false));
            results.addView(ui.text(activity.getString(R.string.events_contact_visibility_note), 11, ui.muted(), false));
        }
        if (weatherStatus != null && !weatherStatus.isEmpty())
            results.addView(ui.text(weatherStatus, 12, ui.muted(), false));
        if (forecast != null) {
            String attribution = forecast.attributions.isEmpty()
                    ? activity.getString(R.string.events_weather_provider)
                    : android.text.TextUtils.join(" · ", forecast.attributions);
            results.addView(ui.text(activity.getString(R.string.events_weather_attribution, attribution),
                    12, ui.muted(), false));
        }
        results.addView(ui.text(plan.message, 14, ui.ink(), false));
        EventObservationPlanner.Slot slot = plan.recommended;
        if (slot == null) {
            results.addView(ui.text(activity.getString(R.string.events_no_booking), 13, ui.muted(), false));
        } else {
            LinearLayout card = ui.card();
            card.addView(ui.text(activity.getString(R.string.events_recommended, dateTime.format(slot.start), dateTime.format(slot.end)), 14, ui.ink(), true));
            card.addView(ui.text(activity.getString(R.string.events_best_time, dateTime.format(slot.bestTime)), 13, ui.ink(), false));
            if (Double.isFinite(slot.altitude) && Double.isFinite(slot.azimuth)) {
                card.addView(ui.text(activity.getString(R.string.events_geometry, slot.altitude, slot.azimuth,
                        slot.moonIllumination * 100.0), 12, ui.muted(), false));
            }
            if (slot.summary != null && !slot.summary.trim().isEmpty())
                card.addView(ui.text(slot.summary, 12, ui.muted(), false));
            WeatherPlanner.Hour usedHour = matchingHour(forecast, slot.bestTime);
            if (slot.weatherKnown && usedHour != null)
                card.addView(ui.text(activity.getString(R.string.events_weather_conditions,
                        weatherValue(usedHour.cloudPercent, "%"), weatherValue(usedHour.precipMm, " mm"),
                        weatherValue(usedHour.humidity, "%"), weatherValue(usedHour.windKmh, " km/h")),
                        12, ui.muted(), false));
            if (event.kind == CelestialEvents.Kind.METEOR_SHOWER)
                card.addView(ui.text(activity.getString(R.string.events_meteor_altitude_note), 12, ui.muted(), false));
            if (event.kind == CelestialEvents.Kind.MOON_PHASE && event.phase == 0)
                card.addView(ui.text(activity.getString(R.string.events_new_moon_note), 12, ui.muted(), false));
            card.addView(ui.text(activity.getString(slot.weatherKnown ? R.string.events_weather_known : R.string.events_weather_unknown), 12, ui.muted(), false));
            if (event.primaryId != null && !event.primaryId.isEmpty())
                card.addView(ui.button(activity.getString(R.string.events_preview), () -> {
                    dismiss(); actions.preview(event.primaryId, slot.bestTime);
                }));
            card.addView(ui.button(activity.getString(R.string.events_book), () ->
                    CalendarBooking.launch(activity, event, slot, place, latitude, longitude, ZoneId.systemDefault())));
            results.addView(card, ui.spaced());
        }
        if (safeWebUrl(event.sourceUrl) != null)
            results.addView(ui.button(activity.getString(R.string.events_source), () -> openSource(event.sourceUrl)));
        if (forecast != null)
            results.addView(ui.button(activity.getString(R.string.events_weather_source), () ->
                    openSource("https://www.qweather.com/")));
        results.addView(ui.button(activity.getString(R.string.events_weather), () -> requestWeather(event)));
    }

    private WeatherPlanner.Hour matchingHour(QWeatherClient.Forecast forecast, Instant time) {
        if (forecast == null || time == null) return null;
        Instant hour = time.truncatedTo(ChronoUnit.HOURS);
        for (WeatherPlanner.Hour candidate : forecast.hours)
            if (candidate.time.equals(hour)) return candidate;
        return null;
    }

    private String weatherValue(double value, String unit) {
        return Double.isFinite(value) ? String.format(Locale.getDefault(), "%.1f%s", value, unit)
                : activity.getString(R.string.events_weather_value_unknown);
    }

    private void requestWeather(CelestialEvents.Event event) {
        if (!credentials.hasPrivateKey()) {
            results.addView(ui.text(activity.getString(R.string.events_weather_missing), 13, ui.muted(), false));
            results.addView(ui.button(activity.getString(R.string.events_configure_weather), () -> {
                dismiss(); actions.configureWeather();
            }));
            return;
        }
        cancel();
        final int request = generation;
        results.removeAllViews();
        results.addView(ui.text(activity.getString(R.string.events_weather_loading), 14, ui.muted(), false));
        final double lat = latitude, lon = longitude;
        final String key = String.format(Locale.US, "%.5f:%.5f", lat, lon);
        task = worker.submit(() -> {
            try {
                QWeatherClient.Forecast forecast = cachedForecast;
                Instant now = Instant.now();
                if (forecast == null || !key.equals(cachedLocation) || forecast.fetchedAt.isAfter(now)
                        || Duration.between(forecast.fetchedAt, now).toMinutes() >= 30) {
                    QWeatherCredentials.Config config = credentials.getConfig();
                    QWeatherClient client = new QWeatherClient();
                    activeClient = client;
                    try { forecast = client.fetch(config.host, credentials.signToken(now), lat, lon); }
                    finally { if (activeClient == client) activeClient = null; }
                    cachedForecast = forecast;
                    cachedLocation = key;
                }
                QWeatherClient.Forecast ready = forecast;
                handler.post(() -> {
                    if (!valid(request)) return;
                    showDetail(event, ready.hours,
                            activity.getString(R.string.events_weather_fresh, dateTime.format(ready.fetchedAt)), ready);
                });
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) return;
                handler.post(() -> { if (valid(request)) showDetail(event, Collections.emptyList(), activity.getString(R.string.events_weather_failed)); });
            }
        });
    }

    private void renderCurrentList() {
        renderList(RANGE_DAYS[Math.max(0, Math.min(RANGE_DAYS.length - 1, rangeSpinner.getSelectedItemPosition()))]);
    }

    private String visibilityText(CelestialEvents.Event event) {
        if (event.kind == CelestialEvents.Kind.SOLAR_ECLIPSE && event.local)
            return activity.getString(R.string.events_local);
        if (event.kind == CelestialEvents.Kind.SOLAR_ECLIPSE)
            return activity.getString(R.string.events_global);
        return activity.getString(R.string.events_geocentric);
    }

    private Uri safeWebUrl(String value) {
        if (value == null) return null;
        Uri uri = Uri.parse(value.trim());
        String scheme = uri.getScheme();
        return ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) ? uri : null;
    }

    private void openSource(String value) {
        Uri uri = safeWebUrl(value);
        if (uri == null) return;
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, R.string.events_source_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private boolean showing() { return !destroyed && dialog != null && dialog.isShowing(); }
    private boolean valid(int request) { return showing() && generation == request; }
    private void cancel() {
        generation++;
        if (task != null) task.cancel(true);
        QWeatherClient client = activeClient;
        if (client != null) client.cancel();
    }
    private void dismiss() {
        cancel();
        if (dialog != null) {
            dialog.setOnDismissListener(null);
            dialog.dismiss();
            dialog = null;
        }
    }
}
