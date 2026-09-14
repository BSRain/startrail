package com.startrail.app;

import android.test.InstrumentationTestCase;

import com.startrail.app.astro.Astronomy;
import com.startrail.app.astro.EquatorialTransform;
import com.startrail.app.astro.SkyCamera;

import java.time.Instant;

/** Regression checks against the scalar coordinate path used by the renderer. */
public final class EquatorialTransformTest extends InstrumentationTestCase {
    private static final double EPSILON = 1e-10;

    public void testMatchesHorizontalAcrossEpochsLocationsAndCoordinateEdges() {
        String[] times = {"1900-01-01T00:00:00Z", "2000-01-01T12:00:00Z", "2100-12-31T23:59:59Z"};
        double[][] locations = {{90, 0}, {-90, 0}, {22.3193, 180}, {-33.8688, -180}, {0, -73.9857}};
        double[][] coordinates = {{0, 0}, {23.999999999, 0}, {0.000000001, 0}, {6, 90}, {18, -90}, {12, 89.999}};
        for (String text : times) for (double[] location : locations) {
            Instant time = Instant.parse(text);
            EquatorialTransform transform = new EquatorialTransform(time, location[0], location[1]);
            for (double[] coordinate : coordinates) assertMatches(transform, time, location, coordinate);
        }
    }

    public void testPackedAndInPlaceTransforms() {
        Instant time = Instant.parse("2026-09-10T04:05:06.123456789Z");
        EquatorialTransform transform = new EquatorialTransform(time, 22.3193, 114.1694);
        double[] packed = new double[6];
        System.arraycopy(EquatorialTransform.fromRaDec(23.9999, -20), 0, packed, 0, 3);
        System.arraycopy(EquatorialTransform.fromRaDec(0.0001, 75), 0, packed, 3, 3);
        double[] expected = new double[6];
        transform.transform(packed, 0, expected, 0, 2);
        transform.transform(packed, 0, packed, 0, 2);
        for (int i = 0; i < packed.length; i++) assertEquals(expected[i], packed[i], 0.0);

        double[] withMagnitude = {0, 0, 0, 4.25};
        transform.transform(EquatorialTransform.fromRaDec(3, 12), withMagnitude);
        assertEquals(4.25, withMagnitude[3], 0.0);
    }

    public void testInputValidation() {
        Instant now = Instant.parse("2000-01-01T12:00:00Z");
        expectIllegalArgument(() -> new EquatorialTransform(null, 0, 0));
        expectIllegalArgument(() -> new EquatorialTransform(now, 91, 0));
        expectIllegalArgument(() -> new EquatorialTransform(now, 0, 181));
        expectIllegalArgument(() -> EquatorialTransform.fromRaDec(24, 0));
        expectIllegalArgument(() -> EquatorialTransform.fromRaDec(0, -91));
        EquatorialTransform transform = new EquatorialTransform(now, 0, 0);
        expectIllegalArgument(() -> transform.transform(new double[2], 0, new double[3], 0));
        expectIllegalArgument(() -> transform.transform(new double[] {Double.NaN, 0, 0}, 0, new double[3], 0));
        expectIllegalArgument(() -> transform.transform(new double[6], 0, new double[3], 0, 2));
    }

    private void assertMatches(EquatorialTransform transform, Instant time,
                               double[] location, double[] coordinate) {
        Astronomy.HorizontalPosition horizontal = Astronomy.horizontal(
                coordinate[0], coordinate[1], time, location[0], location[1]);
        double[] expected = SkyCamera.horizontalVector(
                horizontal.altitudeDegrees, horizontal.azimuthDegrees);
        double[] actual = new double[3];
        transform.transform(EquatorialTransform.fromRaDec(coordinate[0], coordinate[1]), 0, actual, 0);
        for (int i = 0; i < 3; i++)
            assertEquals(time + " location=" + location[0] + "," + location[1]
                    + " ra/dec=" + coordinate[0] + "," + coordinate[1] + " component=" + i,
                    expected[i], actual[i], EPSILON);
    }

    private void expectIllegalArgument(Runnable action) {
        try { action.run(); fail("Expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { /* expected */ }
    }
}
