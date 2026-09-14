package com.startrail.app;

import junit.framework.TestCase;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

public final class JournalBackupTest extends TestCase {
    public void testUnicodeRoundTrip() {
        ObservationStore.Entry source = new ObservationStore.Entry(
                "sirius", "天狼星 ✨", "香港", 1788888888000L,
                22.3193, 114.1694, "云散了；很亮。🌌");
        source.id = "123e4567-e89b-12d3-a456-426614174000";

        List<ObservationStore.Entry> decoded = JournalBackup.decode(
                JournalBackup.encode(Arrays.asList(source)));
        assertEquals(1, decoded.size());
        ObservationStore.Entry actual = decoded.get(0);
        assertEquals(source.id, actual.id);
        assertEquals(source.starId, actual.starId);
        assertEquals(source.target, actual.target);
        assertEquals(source.place, actual.place);
        assertEquals(source.time, actual.time);
        assertEquals(source.latitude, actual.latitude, 0.0);
        assertEquals(source.longitude, actual.longitude, 0.0);
        assertEquals(source.note, actual.note);
    }

    public void testBadSchemaRejected() {
        assertRejected("{\"app\":\"StarTrail\",\"schemaVersion\":2,\"entries\":[]}");
        assertRejected("{\"app\":\"StarTrail\",\"schemaVersion\":1,\"entries\":[]} trailing");
    }

    public void testMalformedFieldsAndNonFiniteNumbersRejected() {
        assertRejected(backup("bad-id", "1", "0", "0"));
        assertRejected(backup("123e4567-e89b-12d3-a456-426614174000", "1.5", "0", "0"));
        assertRejected(backup("123e4567-e89b-12d3-a456-426614174000", "-2208988800001", "0", "0"));
        assertRejected(backup("123e4567-e89b-12d3-a456-426614174000", "1", "NaN", "0"));
        assertRejected(backup("123e4567-e89b-12d3-a456-426614174000", "1", "91", "0"));
        assertRejected(backup("123e4567-e89b-12d3-a456-426614174000", "1", "0", "181"));
    }

    public void testPreEpochTimeRoundTrips() {
        ObservationStore.Entry boundary = new ObservationStore.Entry(
                "", "1900 年观测", "", -2208988800000L, 0, 0, "闰年前的记录");
        boundary.id = "123e4567-e89b-12d3-a456-426614174000";
        ObservationStore.Entry leapDay = new ObservationStore.Entry(
                "", "闰日观测", "", Instant.parse("1968-02-29T12:34:56Z").toEpochMilli(),
                0, 0, "");
        leapDay.id = "123e4567-e89b-12d3-a456-426614174001";
        List<ObservationStore.Entry> decoded = JournalBackup.decode(
                JournalBackup.encode(Arrays.asList(boundary, leapDay)));
        assertEquals(boundary.time, decoded.get(0).time);
        assertEquals(leapDay.time, decoded.get(1).time);
    }

    public void testDeeplyNestedJsonRejectedBeforeParsing() {
        StringBuilder json = new StringBuilder();
        for (int i = 0; i < 100; i++) json.append("{\"x\":");
        json.append('0');
        for (int i = 0; i < 100; i++) json.append('}');
        assertRejected(json.toString());
    }

    public void testOversizeInputRejected() {
        StringBuilder value = new StringBuilder(JournalBackup.MAX_BYTES + 1);
        while (value.length() <= JournalBackup.MAX_BYTES) value.append('x');
        assertRejected(value.toString());
    }

    public void testDuplicateIdsRejectedWithoutPartialResult() {
        String entry = entry("123e4567-e89b-12d3-a456-426614174000", "1", "0", "0");
        assertRejected("{\"app\":\"StarTrail\",\"schemaVersion\":1,\"entries\":[" + entry + "," + entry + "]}");
    }

    private static String backup(String id, String time, String latitude, String longitude) {
        return "{\"app\":\"StarTrail\",\"schemaVersion\":1,\"entries\":[" + entry(id, time, latitude, longitude) + "]}";
    }

    private static String entry(String id, String time, String latitude, String longitude) {
        return "{\"id\":\"" + id + "\",\"starId\":\"\",\"target\":\"目标\","
                + "\"place\":\"\",\"time\":" + time + ",\"latitude\":" + latitude
                + ",\"longitude\":" + longitude + ",\"note\":\"\"}";
    }

    private static void assertRejected(String json) {
        try {
            JournalBackup.decode(json);
            fail("Expected invalid backup to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
