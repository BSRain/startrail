package com.startrail.app;

import android.graphics.Canvas;
import android.graphics.Paint;

/** Compact, asset-free map symbols for deep-sky objects. */
public final class DeepSkyRenderer {
    private static final Paint PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);

    private DeepSkyRenderer() {}

    /** Draws a fixed-size symbol centered at {@code x}, {@code y}. */
    public static synchronized void draw(Canvas canvas, String kind, float x, float y,
                                         float density, boolean redMode) {
        if (canvas == null || kind == null || density <= 0f) return;

        float r = 7f * density;
        int line = redMode ? 0xffc97782 : 0xff8fc7d6;
        int accent = redMode ? 0xffd68a91 : 0xffb9aedb;
        int warm = redMode ? 0xffbc696f : 0xffd3ba72;

        PAINT.setShader(null);
        PAINT.setStrokeCap(Paint.Cap.ROUND);
        PAINT.setStrokeJoin(Paint.Join.ROUND);

        switch (kind) {
            case "galaxy":
                stroke(line, 1.15f * density);
                canvas.save();
                canvas.rotate(-28f, x, y);
                canvas.drawOval(x - r, y - r * .42f, x + r, y + r * .42f, PAINT);
                fill(withAlpha(accent, 105));
                canvas.drawOval(x - r * .46f, y - r * .17f,
                        x + r * .46f, y + r * .17f, PAINT);
                canvas.restore();
                break;

            case "open_cluster":
                stroke(withAlpha(line, 130), .85f * density);
                canvas.drawCircle(x, y, r, PAINT);
                fill(warm);
                dot(canvas, x - .45f * r, y - .26f * r, density);
                dot(canvas, x + .12f * r, y - .54f * r, density);
                dot(canvas, x + .52f * r, y - .05f * r, density);
                dot(canvas, x + .22f * r, y + .42f * r, density);
                dot(canvas, x - .42f * r, y + .40f * r, density);
                dot(canvas, x - .05f * r, y + .04f * r, density);
                break;

            case "globular_cluster":
                stroke(accent, 1.05f * density);
                canvas.drawCircle(x, y, r * .72f, PAINT);
                canvas.drawLine(x - r, y, x + r, y, PAINT);
                canvas.drawLine(x, y - r, x, y + r, PAINT);
                fill(withAlpha(warm, 120));
                canvas.drawCircle(x, y, 1.8f * density, PAINT);
                break;

            case "nebula":
                fill(withAlpha(accent, 35));
                canvas.drawCircle(x - r * .18f, y, r * .72f, PAINT);
                canvas.drawCircle(x + r * .30f, y - r * .16f, r * .54f, PAINT);
                stroke(line, 1.05f * density);
                canvas.drawRect(x - r * .82f, y - r * .82f,
                        x + r * .82f, y + r * .82f, PAINT);
                break;

            case "planetary_nebula":
                stroke(line, 1.15f * density);
                canvas.drawCircle(x, y, r * .76f, PAINT);
                stroke(withAlpha(accent, 155), .8f * density);
                canvas.drawCircle(x, y, r * .43f, PAINT);
                fill(warm);
                canvas.drawCircle(x, y, 1.25f * density, PAINT);
                break;

            default:
                break;
        }
    }

    private static void dot(Canvas canvas, float x, float y, float density) {
        canvas.drawCircle(x, y, 1.05f * density, PAINT);
    }

    private static void fill(int color) {
        PAINT.setStyle(Paint.Style.FILL);
        PAINT.setColor(color);
    }

    private static void stroke(int color, float width) {
        PAINT.setStyle(Paint.Style.STROKE);
        PAINT.setStrokeWidth(width);
        PAINT.setColor(color);
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00ffffff);
    }
}
