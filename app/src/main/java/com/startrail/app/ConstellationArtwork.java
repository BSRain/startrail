package com.startrail.app;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;

import com.startrail.app.astro.Astronomy;
import com.startrail.app.astro.SkyCamera;
import com.startrail.app.astro.Star;
import com.startrail.app.astro.StarCatalog;

import java.time.Instant;

/**
 * Original, ornamental constellation figures drawn directly on the celestial sphere.
 * Every vertex is an equatorial tangent-plane offset from a star in {@link StarCatalog};
 * there are no screen-fixed sprites or external artwork assets.
 */
public final class ConstellationArtwork {
    private static final float B = Float.NaN; // detail-stroke break
    private static final float[][] CYGNUS = {{-14,1},{-10,-3},{-3,-5},{3,-4},{7,-1},{8,3},{9,8},{11,13},{14,15},{17,14},{14,12},{12,9},{11,4},{9,0},{5,4},{1,11},{-2,7},{-7,5}};
    private static final float[][] CYGNUS_DETAIL = {{-12,1},{-2,1},{7,-1},{B,B},{-7,4},{-1,13},{1,4},{B,B},{-4,4},{-8,10},{0,5},{B,B},{-1,2},{4,8},{6,0},{B,B},{12,12},{14,14},{16,14}};
    private static final float[][] LYRA = {{-9,-10},{-8,5},{-5,11},{-2,12},{-3,8},{-4,-6},{4,-6},{3,8},{2,12},{5,11},{8,5},{9,-10},{6,-7},{-6,-7}};
    private static final float[][] LYRA_DETAIL = {{-8,5},{8,5},{B,B},{-7,1},{7,1},{B,B},{-6,-3},{6,-3},{B,B},{-4,-6},{-3,8},{B,B},{-1,-6},{-1,9},{B,B},{2,-6},{1,9},{B,B},{4,-6},{3,8}};
    private static final float[][] ORION = {{-2,-13},{-4,-11},{-4,-8},{-8,-7},{-13,-2},{-11,0},{-6,-3},{-4,1},{-5,6},{-9,13},{-6,15},{0,8},{6,15},{9,13},{5,6},{4,1},{6,-3},{11,0},{13,-2},{8,-7},{4,-8},{4,-11},{2,-13}};
    private static final float[][] ORION_DETAIL = {{-2,-11},{2,-11},{B,B},{-7,-6},{7,-6},{B,B},{-4,0},{4,0},{B,B},{-3,3},{3,3},{B,B},{0,-8},{0,8},{B,B},{-11,-1},{-13,-8},{B,B},{11,-1},{13,-8}};
    private static final float[][] BEAR = {{-14,3},{-11,-2},{-6,-4},{0,-4},{6,-2},{11,1},{13,5},{11,8},{7,7},{5,4},{0,5},{-2,10},{-5,10},{-5,5},{-10,7}};
    private static final float[][] BEAR_DETAIL = {{-12,2},{-10,1},{-8,1},{-5,-3},{-4,-9},{-1,-9},{0,-4},{5,-2},{6,-8},{9,-8},{10,0},{12,4}};
    private static final float[][] QUEEN = {{-7,-12},{-3,-9},{-5,-5},{-3,-1},{-7,5},{-5,12},{0,8},{4,11},{7,7},{4,2},{6,-3},{3,-8},{0,-6},{-3,-12}};
    private static final float[][] QUEEN_DETAIL = {{-3,-9},{0,-13},{3,-8},{0,-6},{-3,-1},{4,2},{-5,5},{4,11},{0,8},{0,-6},{-8,-5},{-10,1},{-7,5}};
    private static final float[][] SCORPION = {{-11,-7},{-7,-10},{-3,-8},{0,-4},{1,1},{4,5},{8,6},{11,3},{10,-1},{7,-2},{6,1},{8,3},{5,3},{2,0},{1,-5},{-2,-11},{-7,-13}};
    private static final float[][] SCORPION_DETAIL = {{-5,-9},{-10,-2},{-13,0},{-10,1},{-5,-6},{-1,-3},{3,1},{6,4},{9,4},{11,1},{9,-1},{7,0}};
    private static final float[][] PEGASUS = {{-12,4},{-8,0},{-4,-2},{0,-2},{5,-5},{11,-4},{8,0},{4,2},{10,5},{14,10},{8,9},{3,6},{1,12},{-2,15},{-3,8},{-8,10}};
    private static final float[][] PEGASUS_DETAIL = {{-8,1},{-3,2},{3,2},{8,-2},{11,-4},{8,0},{12,4},{14,10},{8,7},{2,5},{-1,8},{-8,10},{-5,4},{-11,4},{-14,1}};

    private static final String[] NAMES = {"Cygnus · 天鹅座", "Lyra · 天琴座", "Orion · 猎户座",
            "Ursa Major · 大熊座", "Cassiopeia · 仙后座", "Scorpius · 天蝎座", "Pegasus · 飞马座"};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /* Mutable projected vertices belong to this renderer, never to static process state. */
    private final Figure[] figures;
    private Instant time;
    private double latitude;
    private double longitude;

    public ConstellationArtwork() {
        figures = new Figure[] {
                new Figure(NAMES[0], "sadr", -18, 1.05, CYGNUS, CYGNUS_DETAIL, -5.5f, 14f, 1.5f),
                new Figure(NAMES[1], "vega", 7, .72, LYRA, LYRA_DETAIL, 0f, -8f, 1.25f),
                new Figure(NAMES[2], "alnilam", 0, .78, ORION, ORION_DETAIL, 0f, -10.5f, 2.2f),
                new Figure(NAMES[3], "alioth", -18, 1.05, BEAR, BEAR_DETAIL, 11f, 4.5f, 2.2f),
                new Figure(NAMES[4], "gamma_cas", 15, .82, QUEEN, QUEEN_DETAIL, 0f, -9f, 2.1f),
                new Figure(NAMES[5], "antares", -18, .92, SCORPION, SCORPION_DETAIL, -6f, -9f, 1.4f),
                new Figure(NAMES[6], "scheat", 12, 1.0, PEGASUS, PEGASUS_DETAIL, 10.5f, -3f, 2f)
        };
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setTypeface(Typeface.create("sans-serif-smallcaps", Typeface.NORMAL));
    }

    public static int count() { return NAMES.length; }

    public static String[] names() {
        return NAMES.clone();
    }

    public void update(Instant instant, double latitudeDegrees, double longitudeDegrees) {
        if (instant == null) throw new IllegalArgumentException("instant");
        if (latitudeDegrees < -90 || latitudeDegrees > 90 || longitudeDegrees < -180 || longitudeDegrees > 180)
            throw new IllegalArgumentException("location");
        time = instant;
        latitude = latitudeDegrees;
        longitude = longitudeDegrees;
        for (Figure figure : figures) figure.update(instant, latitudeDegrees, longitudeDegrees);
    }

    public void draw(Canvas canvas, SkyCamera.Basis camera, double fovDegrees,
                     int width, int height, boolean redMode, boolean day) {
        if (canvas == null || camera == null || time == null || width <= 0 || height <= 0) return;
        float fade = (float) clamp((118.0 - fovDegrees) / 42.0, .16, 1.0);
        float light = day ? .48f : 1f;
        int lineRgb = redMode ? 0xffd77989 : 0xffb9ddec;
        int accentRgb = redMode ? 0xffed9aa5 : 0xffe3c984;

        for (Figure figure : figures) {
            SkyCamera.Point anchor = SkyCamera.project(figure.anchorEnu, camera, fovDegrees, width, height);
            if (anchor.depth <= .035) continue;

            Path silhouette = projectedPath(figure.outlineEnu, camera, fovDegrees, width, height, true);
            if (silhouette != null) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(withAlpha(lineRgb, (int)(18 * fade * light)));
                canvas.drawPath(silhouette, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(5f);
                paint.setColor(withAlpha(lineRgb, (int)(24 * fade * light)));
                canvas.drawPath(silhouette, paint);
                paint.setStrokeWidth(1.45f);
                paint.setColor(withAlpha(accentRgb, (int)(145 * fade * light)));
                canvas.drawPath(silhouette, paint);
            }

            drawSegmented(canvas, figure.detailEnu, camera, fovDegrees, width, height,
                    withAlpha(lineRgb, (int)(112 * fade * light)), 1.05f);
            drawKnots(canvas, figure.detailEnu, camera, fovDegrees, width, height,
                    withAlpha(accentRgb, (int)(110 * fade * light)));
            float[] bounds = projectedBounds(figure.outlineEnu, camera, fovDegrees, width, height);
            if (bounds != null && fovDegrees < 104) {
                paint.setStyle(Paint.Style.FILL);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(Math.max(16f, Math.min(22f, width / 50f)));
                paint.setColor(withAlpha(accentRgb, (int)(175 * fade * light)));
                float labelY = bounds[3] + paint.getTextSize() * 1.45f;
                if (labelY > height - paint.getTextSize() * .3f) labelY = bounds[1] - paint.getTextSize() * .7f;
                canvas.drawText(figure.name, (bounds[0] + bounds[2]) * .5f, labelY, paint);
            }
        }
    }

    private Path projectedPath(double[][] vertices, SkyCamera.Basis camera, double fov, int width, int height, boolean close) {
        float[] xs = new float[vertices.length], ys = new float[vertices.length];
        for (int i = 0; i < vertices.length; i++) {
            double[] vertex = vertices[i];
            SkyCamera.Point point = SkyCamera.project(vertex, camera, fov, width, height);
            if (point.depth <= .035 || !reasonable(point, width, height)) return null;
            xs[i] = (float) point.x; ys[i] = (float) point.y;
        }
        if (vertices.length == 0) return null;
        Path path = new Path();
        if (close && vertices.length > 2) {
            float sx = (xs[0] + xs[vertices.length - 1]) * .5f;
            float sy = (ys[0] + ys[vertices.length - 1]) * .5f;
            path.moveTo(sx, sy);
            for (int i = 0; i < vertices.length; i++) {
                int next = (i + 1) % vertices.length;
                path.quadTo(xs[i], ys[i], (xs[i] + xs[next]) * .5f, (ys[i] + ys[next]) * .5f);
            }
            path.close();
        } else {
            path.moveTo(xs[0], ys[0]);
            for (int i = 1; i < vertices.length; i++) path.lineTo(xs[i], ys[i]);
        }
        return path;
    }

    private void drawSegmented(Canvas canvas, double[][] vertices, SkyCamera.Basis camera, double fov,
                               int width, int height, int color, float strokeWidth) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(color);
        Path path = new Path();
        boolean started = false;
        SkyCamera.Point previous = null;
        for (double[] vertex : vertices) {
            if (vertex == null) { previous = null; if (started) { canvas.drawPath(path, paint); path.reset(); started=false; } continue; }
            SkyCamera.Point point = SkyCamera.project(vertex, camera, fov, width, height);
            boolean usable = point.depth > .035 && reasonable(point, width, height);
            if (usable && previous != null && previous.depth > .035 && reasonable(previous, width, height)) {
                if (!started) path.moveTo((float)previous.x, (float)previous.y);
                path.lineTo((float)point.x, (float)point.y);
                started = true;
            } else if (started) {
                canvas.drawPath(path, paint); path.reset(); started = false;
            }
            previous = usable ? point : null;
        }
        if (started) canvas.drawPath(path, paint);
    }

    private void drawKnots(Canvas canvas, double[][] vertices, SkyCamera.Basis camera, double fov,
                           int width, int height, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        for (int i = 0; i < vertices.length; i += 4) {
            if (vertices[i] == null) continue;
            SkyCamera.Point p = SkyCamera.project(vertices[i], camera, fov, width, height);
            if (p.onScreen && p.depth > .035) canvas.drawCircle((float)p.x, (float)p.y, 1.25f, paint);
        }
    }

    private float[] projectedBounds(double[][] vertices, SkyCamera.Basis camera, double fov, int width, int height) {
        float left=Float.MAX_VALUE, top=Float.MAX_VALUE, right=-Float.MAX_VALUE, bottom=-Float.MAX_VALUE;
        boolean any=false;
        for (double[] v:vertices) { if(v==null)continue; SkyCamera.Point p=SkyCamera.project(v,camera,fov,width,height);
            if(!p.onScreen||p.depth<=.035)continue; any=true; left=Math.min(left,(float)p.x);right=Math.max(right,(float)p.x);top=Math.min(top,(float)p.y);bottom=Math.max(bottom,(float)p.y); }
        return any?new float[]{left,top,right,bottom}:null;
    }

    private static boolean reasonable(SkyCamera.Point p, int width, int height) {
        return Double.isFinite(p.x) && Double.isFinite(p.y)
                && p.x > -width * 1.5 && p.x < width * 2.5
                && p.y > -height * 1.5 && p.y < height * 2.5;
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00ffffff);
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    private static final class Figure {
        final String name;
        final Star anchor;
        final double rotation;
        final double scale;
        final float[][] outline;
        final float[][] detail;
        final float headX, headY, headRadius;
        double[] anchorEnu = {0, 0, -1};
        double[] headEnu = {0, 0, -1};
        double[] headRimEnu = {0, 0, -1};
        double[][] outlineEnu;
        double[][] detailEnu;

        Figure(String name, String anchorId, double rotation, double scale,
               float[][] outline, float[][] detail, float headX, float headY, float headRadius) {
            this.name = name;
            this.anchor = StarCatalog.findById(anchorId);
            if (anchor == null) throw new IllegalStateException("Missing catalog anchor " + anchorId);
            this.rotation = Math.toRadians(rotation);
            this.scale = scale;
            this.outline = outline;
            this.detail = detail;
            this.headX = headX; this.headY = headY; this.headRadius = headRadius;
        }

        void update(Instant time, double latitude, double longitude) {
            anchorEnu = enu(anchor.raHours, anchor.decDegrees, time, latitude, longitude);
            outlineEnu = convert(outline, time, latitude, longitude);
            detailEnu = convert(detail, time, latitude, longitude);
            headEnu = convert(new float[][]{{headX, headY}}, time, latitude, longitude)[0];
            headRimEnu = convert(new float[][]{{headX + headRadius, headY}}, time, latitude, longitude)[0];
        }

        private double[][] convert(float[][] source, Instant time, double latitude, double longitude) {
            double[][] result = new double[source.length][];
            double c = Math.cos(rotation), s = Math.sin(rotation);
            double cosDec = Math.max(.18, Math.cos(Math.toRadians(anchor.decDegrees)));
            for (int i = 0; i < source.length; i++) {
                if (Float.isNaN(source[i][0])) { result[i] = null; continue; }
                // Artwork coordinates follow Canvas/SVG convention (positive Y is down).
                // Reflect Y before rotating into the east/north celestial tangent plane.
                double east = scale * (source[i][0] * c + source[i][1] * s);
                double north = scale * (source[i][0] * s - source[i][1] * c);
                double ra = anchor.raHours + east / (15.0 * cosDec);
                ra = (ra % 24 + 24) % 24;
                double dec = Math.max(-89.5, Math.min(89.5, anchor.decDegrees + north));
                result[i] = enu(ra, dec, time, latitude, longitude);
            }
            return result;
        }

        private static double[] enu(double ra, double dec, Instant time, double latitude, double longitude) {
            Astronomy.HorizontalPosition h = Astronomy.horizontal(ra, dec, time, latitude, longitude);
            return SkyCamera.horizontalVector(h.altitudeDegrees, h.azimuthDegrees);
        }
    }
}
