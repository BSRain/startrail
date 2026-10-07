package com.startrail.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.provider.CalendarContract;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.startrail.app.astro.CelestialEvents;
import com.startrail.app.astro.EventObservationPlanner;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;

@SuppressWarnings("deprecation")
public final class EventCalendar43Test extends ActivityInstrumentationTestCase2<MainActivity> {
    private EventCalendarDialog controller;

    public EventCalendar43Test() { super(MainActivity.class); }

    public void testCalendarDraftUsesUtcMillisecondsAcrossLocalMidnight() {
        Instant peak = Instant.parse("2026-12-31T16:30:00Z");
        CelestialEvents.Event event = event(peak);
        EventObservationPlanner.Slot slot = new EventObservationPlanner.Slot(
                Instant.parse("2026-12-31T15:50:00Z"), Instant.parse("2026-12-31T17:10:00Z"),
                peak, 42.0, 183.0, 0.12, false, "测试建议");

        Intent intent = CalendarBooking.createIntent(event, slot, "香港", 22.3, 114.2, ZoneId.of("Asia/Hong_Kong"));

        assertEquals(Intent.ACTION_INSERT, intent.getAction());
        assertEquals(CalendarContract.Events.CONTENT_URI, intent.getData());
        assertEquals(slot.start.toEpochMilli(), intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1));
        assertEquals(slot.end.toEpochMilli(), intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1));
        assertFalse(intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true));
        String description = intent.getStringExtra(CalendarContract.Events.DESCRIPTION);
        assertTrue(description.contains("Asia/Hong_Kong"));
        assertTrue(description.contains("天气未知"));
        assertTrue(description.contains("测试建议"));
        assertTrue(description.contains("不会自动随天气更新"));
        assertTrue(description.contains("2027-01-01"));
        assertFalse(intent.hasExtra(CalendarContract.Events.HAS_ALARM));
    }

    public void testCalendarDraftRejectsSlotOutsideEventWindow() {
        Instant peak = Instant.parse("2027-01-01T00:00:00Z");
        CelestialEvents.Event event = event(peak);
        EventObservationPlanner.Slot slot = new EventObservationPlanner.Slot(
                event.start.minusSeconds(1), peak, peak, 30.0, 180.0, 0.2,
                false, "越界测试");
        try {
            CalendarBooking.createIntent(event, slot, "香港", 22.3, 114.2, ZoneId.of("Asia/Hong_Kong"));
            fail("a booking must not extend beyond its event window");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("event window"));
        }
    }

    public void testAutomaticWeatherOnlyAppliesToLiveEventsIntersectingNext72Hours() {
        Instant now = Instant.parse("2027-01-01T00:00:00Z");
        CelestialEvents.Event soon = event(now.plusSeconds(3600));
        assertTrue(EventCalendarDialog.shouldAutomaticallyRequestWeather(soon, now, true, false));
        assertFalse(EventCalendarDialog.shouldAutomaticallyRequestWeather(soon, now, false, false));
        assertFalse(EventCalendarDialog.shouldAutomaticallyRequestWeather(soon, now, true, true));
        assertFalse(EventCalendarDialog.shouldAutomaticallyRequestWeather(
                event(now.plusSeconds(80 * 3600)), now, true, false));
        assertFalse(EventCalendarDialog.shouldAutomaticallyRequestWeather(
                event(now.minusSeconds(2 * 3600)), now, true, false));
    }

    public void testAutomaticWeatherCacheMustMatchLocationAndBeYoungerThan30Minutes() {
        Instant now = Instant.parse("2027-01-01T00:00:00Z");
        assertTrue(EventCalendarDialog.isFreshLocationCache(
                now.minusSeconds(29 * 60), "22.30000:114.20000", "22.30000:114.20000", now));
        assertFalse(EventCalendarDialog.isFreshLocationCache(
                now.minusSeconds(30 * 60), "22.30000:114.20000", "22.30000:114.20000", now));
        assertFalse(EventCalendarDialog.isFreshLocationCache(
                now.minusSeconds(60), "39.90000:116.40000", "22.30000:114.20000", now));
    }

    public void testMeteorLocalFlagDoesNotClaimLocalVisibilityAndApproximatePeakIsEstimated() throws Throwable {
        MainActivity activity = getActivity();
        Instant now = Instant.now();
        CelestialEvents.Event approximateMeteor = new CelestialEvents.Event(
                "fixture-meteor", "测试流星雨", "用于界面测试。", "https://example.com/source",
                CelestialEvents.Kind.METEOR_SHOWER, now.plusSeconds(24 * 3600),
                now.plusSeconds(3600), now.plusSeconds(48 * 3600), "", "", true, true,
                -1, Double.NaN, 5.5, 20.0, Collections.emptyList());
        runTestOnUiThread(() -> {
            controller = controller(activity);
            controller.show(22.3, 114.2, "香港 · 测试地点");
        });
        // Spinner selection callbacks enqueue list reloads during show(); wait until they settle
        // before replacing the list with this deterministic detail fixture.
        waitForText("查看观测建议", 15000);
        runTestOnUiThread(() -> invokeShowDetail(controller, approximateMeteor));
        waitForText("预计峰值", 5000);
        AlertDialog dialog = (AlertDialog) field(controller, "dialog");
        View root = dialog.getWindow().getDecorView();
        assertNotNull(find(root, "预计峰值"));
        assertNotNull(find(root, "详情将结合所选地点判断可见性"));
        assertNull(find(root, "本地点可见事件"));
    }

    public void testApplicationDoesNotRequestCalendarWritePermission() throws Exception {
        PackageInfo info = getActivity().getPackageManager().getPackageInfo(
                getActivity().getPackageName(), android.content.pm.PackageManager.GET_PERMISSIONS);
        if (info.requestedPermissions != null) {
            assertFalse(Arrays.asList(info.requestedPermissions).contains("android.permission.WRITE_CALENDAR"));
            assertFalse(Arrays.asList(info.requestedPermissions).contains("android.permission.READ_CALENDAR"));
        }
    }

    public void testDialogStartsAstronomyOnlyAndDestroyCancelsWork() throws Throwable {
        MainActivity activity = getActivity();
        runTestOnUiThread(() -> {
            controller = new EventCalendarDialog(activity, new EventCalendarDialog.Actions() {
                public void preview(String id, Instant time) { }
                public void locate() { }
                public void configureWeather() { }
            });
            controller.show(22.3, 114.2, "测试地点");
        });
        getInstrumentation().waitForIdleSync();
        AlertDialog dialog = (AlertDialog) field(controller, "dialog");
        assertTrue(dialog.isShowing());
        assertNull("opening the calendar must not start a weather client", field(controller, "activeClient"));
        runTestOnUiThread(() -> controller.destroy());
        assertFalse(dialog.isShowing());
        assertTrue((Boolean) field(controller, "destroyed"));
    }

    public void testEventListLoadsAndSelectedEventProducesPlannerResult() throws Throwable {
        MainActivity activity = getActivity();
        runTestOnUiThread(() -> {
            controller = controller(activity);
            controller.show(22.3, 114.2, "香港 · 测试地点");
        });
        waitForText("查看观测建议", 15000);
        @SuppressWarnings("unchecked")
        java.util.List<CelestialEvents.Event> loaded =
                (java.util.List<CelestialEvents.Event>) field(controller, "events");
        assertFalse(loaded.isEmpty());
        CelestialEvents.Event selected = loaded.get(0);
        runTestOnUiThread(() -> invokeShowDetail(controller, selected));
        waitForAnyText(15000, "推荐观测", "没有可推荐", "没有满足高度", "观测窗口已经结束");
    }

    private CelestialEvents.Event event(Instant peak) {
        return new CelestialEvents.Event("test-event", "测试天象", "用于预约意图测试。",
                "https://example.com/source", CelestialEvents.Kind.CLOSE_APPROACH, peak,
                peak.minusSeconds(3600), peak.plusSeconds(3600), "moon", "venus",
                false, true, -1, 1.2, Double.NaN, Double.NaN, Collections.emptyList());
    }

    private EventCalendarDialog controller(MainActivity activity) {
        return new EventCalendarDialog(activity, new EventCalendarDialog.Actions() {
            public void preview(String id, Instant time) { }
            public void locate() { }
            public void configureWeather() { }
        });
    }

    private void invokeShowDetail(EventCalendarDialog target, CelestialEvents.Event event) {
        try {
            java.lang.reflect.Method method = EventCalendarDialog.class.getDeclaredMethod(
                    "showDetail", CelestialEvents.Event.class, java.util.List.class, String.class);
            method.setAccessible(true);
            method.invoke(target, event, Collections.emptyList(), null);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private View waitForText(String text, long timeoutMs) throws Exception {
        long deadline = android.os.SystemClock.uptimeMillis() + timeoutMs;
        do {
            getInstrumentation().waitForIdleSync();
            AlertDialog dialog = (AlertDialog) field(controller, "dialog");
            View found = find(dialog.getWindow().getDecorView(), text);
            if (found != null) return found;
            android.os.SystemClock.sleep(50);
        } while (android.os.SystemClock.uptimeMillis() < deadline);
        fail("timed out waiting for text: " + text);
        return null;
    }

    private View waitForAnyText(long timeoutMs, String... choices) throws Exception {
        long deadline = android.os.SystemClock.uptimeMillis() + timeoutMs;
        do {
            getInstrumentation().waitForIdleSync();
            AlertDialog dialog = (AlertDialog) field(controller, "dialog");
            for (String choice : choices) {
                View found = find(dialog.getWindow().getDecorView(), choice);
                if (found != null) return found;
            }
            android.os.SystemClock.sleep(50);
        } while (android.os.SystemClock.uptimeMillis() < deadline);
        fail("timed out waiting for planner result");
        return null;
    }

    private View find(View view, String text) {
        if (view instanceof TextView && ((TextView) view).getText().toString().contains(text)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private Object field(Object target, String name) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    @Override protected void tearDown() throws Exception {
        if (controller != null) getInstrumentation().runOnMainSync(() -> controller.destroy());
        super.tearDown();
    }
}
