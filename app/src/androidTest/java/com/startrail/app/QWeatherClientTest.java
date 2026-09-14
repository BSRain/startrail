package com.startrail.app;

import com.startrail.app.astro.WeatherPlanner;

import org.json.JSONException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import junit.framework.TestCase;

/** Offline contract coverage for the QWeather parser and request safety boundaries. */
public final class QWeatherClientTest extends TestCase {
    private static final Instant FETCHED = Instant.parse("2026-09-12T08:00:00Z");

    public void testParsesFractionalValuesOffsetTimeMetadataAndGap() throws Exception {
        QWeatherClient.Forecast forecast = QWeatherClient.parse("{"
                + "\"metadata\":{\"tag\":\"weather-72h\",\"attributions\":[\"和风天气\",\"数据许可条款\"]},"
                + "\"hours\":["
                + hour("2024-05-31T11:00+08:00", "0.76", "0.92", "3.42", "m/s", "0.09", "mm") + ","
                + hour("2024-05-31T13:00+08:00", "0.50", "0.10", "12", "km/h", "1.5", "mm")
                + "]}", FETCHED);
        assertEquals(FETCHED, forecast.fetchedAt);
        assertEquals("weather-72h", forecast.tag);
        assertEquals(2, forecast.attributions.size());
        assertEquals("和风天气", forecast.attributions.get(0));
        assertEquals(2, forecast.hours.size());
        WeatherPlanner.Hour first = forecast.hours.get(0);
        assertEquals(Instant.parse("2024-05-31T03:00:00Z"), first.time);
        assertEquals(92.0, first.cloudPercent, 0.0001);
        assertEquals(76.0, first.humidity, 0.0001);
        assertEquals(12.312, first.windKmh, 0.0001);
        assertEquals(0.09, first.precipMm, 0.0001);
        assertEquals(Instant.parse("2024-05-31T05:00:00Z"), forecast.hours.get(1).time);
        try { forecast.hours.add(first); fail("hours must be immutable"); }
        catch (UnsupportedOperationException expected) { }
    }

    public void testMissingMeasurementsRemainUnknown() throws Exception {
        QWeatherClient.Forecast result = QWeatherClient.parse(
                "{\"hours\":[{\"forecastTime\":\"2024-05-31T03:00Z\"}]}", FETCHED);
        WeatherPlanner.Hour hour = result.hours.get(0);
        assertTrue(Double.isNaN(hour.cloudPercent));
        assertTrue(Double.isNaN(hour.humidity));
        assertTrue(Double.isNaN(hour.windKmh));
        assertTrue(Double.isNaN(hour.precipMm));
    }

    public void testUnknownUnitsRemainUnknown() throws Exception {
        QWeatherClient.Forecast result = QWeatherClient.parse("{\"hours\":["
                + hour("2024-05-31T03:00Z", "0.5", "0.5", "3", "knots", "2", "in")
                + "]}", FETCHED);
        assertTrue(Double.isNaN(result.hours.get(0).windKmh));
        assertTrue(Double.isNaN(result.hours.get(0).precipMm));
    }

    public void testRejectsDuplicateTimeAndInvalidRanges() throws Exception {
        assertInvalid("{\"hours\":["
                + hour("2024-05-31T03:00Z", "0.5", "0.5", "3", "m/s", "0", "mm") + ","
                + hour("2024-05-31T04:00+01:00", "0.5", "0.5", "3", "m/s", "0", "mm") + "]}");
        assertInvalid("{\"hours\":["
                + hour("2024-05-31T03:00Z", "1.01", "0.5", "3", "m/s", "0", "mm") + "]}");
        assertInvalid("{\"hours\":["
                + hour("2024-05-31T03:00Z", "0.5", "-0.01", "3", "m/s", "0", "mm") + "]}");
        assertInvalid("{\"hours\":["
                + hour("2024-05-31T03:30Z", "0.5", "0.5", "3", "m/s", "0", "mm") + "]}");
    }

    public void testRejectsMoreThan240RecordsAndOversizeUtf8() throws Exception {
        StringBuilder records = new StringBuilder("{\"hours\":[");
        for (int i = 0; i < 241; i++) {
            if (i != 0) records.append(',');
            records.append("{\"forecastTime\":\"2024-05-31T03:00Z\"}");
        }
        records.append("]}");
        assertInvalid(records.toString());
        String oversized = "{\"hours\":[],\"padding\":\"" + repeat('界', QWeatherClient.MAX_RESPONSE_BYTES / 3 + 1) + "\"}";
        assertInvalid(oversized);
        byte[] bytes = new byte[QWeatherClient.MAX_RESPONSE_BYTES + 1];
        try { QWeatherClient.readBounded(new ByteArrayInputStream(bytes)); fail("oversize read"); }
        catch (IOException expected) { }
    }

    public void testOnlyQWeatherHttpsHostCanReceiveCredentials() throws Exception {
        assertEquals("abc.qweatherapi.com", QWeatherClient.validateHost("ABC.qweatherapi.com"));
        String[] rejected = {"qweatherapi.com", "evilqweatherapi.com", "abc.qweatherapi.com.evil.test",
                "https://abc.qweatherapi.com", "abc.qweatherapi.com/path", "abc.qweatherapi.com."};
        for (String host : rejected) {
            try { QWeatherClient.validateHost(host); fail("accepted host: " + host); }
            catch (IOException expected) { }
        }
    }

    public void testCancelBeforeFetchPreventsNetworkAndClientCannotBeReused() throws Exception {
        QWeatherClient client = new QWeatherClient();
        client.cancel();
        try {
            client.fetch("abc.qweatherapi.com", "secret-token", 22.3, 114.2);
            fail("canceled client attempted a request");
        } catch (IOException expected) {
            assertEquals("Weather request interrupted", expected.getMessage());
        }
        try {
            client.fetch("abc.qweatherapi.com", "secret-token", 22.3, 114.2);
            fail("single-use client was reused");
        } catch (IOException expected) {
            assertEquals("Weather client is single-use", expected.getMessage());
        }
    }

    private static void assertInvalid(String json) throws Exception {
        try { QWeatherClient.parse(json, FETCHED); fail("invalid response accepted"); }
        catch (JSONException expected) { }
    }

    private static String hour(String time, String humidity, String cloud, String wind,
                               String windUnit, String precipitation, String precipitationUnit) {
        return "{\"forecastTime\":\"" + time + "\",\"humidity\":" + humidity
                + ",\"cloudCover\":" + cloud + ",\"wind\":{\"speed\":{\"value\":" + wind
                + ",\"unit\":\"" + windUnit + "\"}},\"precipitation\":{\"amount\":{\"value\":"
                + precipitation + ",\"unit\":\"" + precipitationUnit + "\"}}}";
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, value);
        return new String(chars);
    }
}
