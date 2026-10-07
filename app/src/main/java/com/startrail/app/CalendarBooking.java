package com.startrail.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.provider.CalendarContract;
import android.widget.Toast;

import com.startrail.app.astro.CelestialEvents;
import com.startrail.app.astro.EventObservationPlanner;

import java.time.ZoneId;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Creates a user-confirmed system-calendar draft; this class never writes calendar data itself. */
final class CalendarBooking {
    private CalendarBooking() {}

    static Intent createIntent(CelestialEvents.Event event, EventObservationPlanner.Slot slot,
                               String place, double latitude, double longitude, ZoneId zone) {
        if (event == null || slot == null || zone == null) throw new IllegalArgumentException("event, slot and zone are required");
        if (!slot.start.isBefore(slot.end) || slot.bestTime.isBefore(slot.start) || slot.bestTime.isAfter(slot.end))
            throw new IllegalArgumentException("slot times must satisfy start < end and contain bestTime");
        if (slot.start.isBefore(event.start) || slot.end.isAfter(event.end))
            throw new IllegalArgumentException("slot must stay within the event window");
        DateTimeFormatter local = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z", Locale.getDefault()).withZone(zone);
        String weather = slot.weatherKnown ? "天气预报已纳入推荐。" : "该时段天气未知，请临行前复核。";
        String solar = event.kind == CelestialEvents.Kind.SOLAR_ECLIPSE
                ? "\n安全提示：切勿直视太阳；必须使用合格的太阳观测滤镜。" : "";
        String source = event.sourceUrl == null || event.sourceUrl.trim().isEmpty()
                ? "" : "\n来源：" + event.sourceUrl.trim();
        String summary = slot.summary == null || slot.summary.trim().isEmpty() ? "" : "\n说明：" + slot.summary.trim();
        String description = event.description + "\n事件峰值：" + local.format(event.peak)
                + "\n推荐观测：" + local.format(slot.start) + " – " + local.format(slot.end)
                + "\n最佳时刻：" + local.format(slot.bestTime) + "\n时区：" + zone.getId()
                + "\n" + weather + " 此日历项目不会自动随天气更新。" + summary + solar + source;
        String coordinates = String.format(Locale.US, "%.5f, %.5f", latitude, longitude);
        String location = place == null || place.trim().isEmpty() ? coordinates : place.trim() + " (" + coordinates + ")";
        return new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, slot.start.toEpochMilli())
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, slot.end.toEpochMilli())
                .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false)
                .putExtra(CalendarContract.Events.TITLE, "观测：" + event.title)
                .putExtra(CalendarContract.Events.EVENT_LOCATION, location)
                .putExtra(CalendarContract.Events.DESCRIPTION, description);
    }

    static void launch(Activity activity, CelestialEvents.Event event, EventObservationPlanner.Slot slot,
                       String place, double latitude, double longitude, ZoneId zone) {
        if (slot == null || !slot.end.isAfter(Instant.now())) {
            Toast.makeText(activity, R.string.events_booking_expired, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            activity.startActivity(createIntent(event, slot, place, latitude, longitude, zone));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(activity, R.string.events_calendar_unavailable, Toast.LENGTH_LONG).show();
        }
    }
}
