package com.startrail.app.astro;

/** Pure-Java camera geometry in a local East, North, Up (ENU) world frame. */
public final class SkyCamera {
    private static final double EPSILON = 1e-9;

    private SkyCamera() {}

    public static class Basis {
        public final double[] right;
        public final double[] up;
        public final double[] forward;

        public Basis(double[] right, double[] up, double[] forward) {
            this.forward = unit(forward);
            double[] projectedRight = subtract(right, scale(this.forward, dot(right, this.forward)));
            this.right = unit(projectedRight);
            // A camera whose local view direction is -Z has right x up = -forward.
            this.up = unit(cross(this.right, this.forward));
        }
    }

    /** Renderer-friendly name for a camera basis. */
    public static final class Frame extends Basis {
        public Frame(double[] right, double[] up, double[] forward) { super(right, up, forward); }
    }

    public static final class Point {
        public final double x;
        public final double y;
        public final double depth;
        public final boolean visible;
        public final boolean inFront;
        public final boolean onScreen;

        private Point(double x, double y, double depth, boolean visible) {
            this.x = x;
            this.y = y;
            this.depth = depth;
            this.visible = visible;
            this.inFront = depth > EPSILON;
            this.onScreen = visible;
        }
    }

    /** Makes a level camera at azimuth 0=north, 90=east, then applies elevation and bank. */
    public static Basis manualBasis(double azimuthDegrees, double elevationDegrees,
                                    double bankDegrees) {
        double az = Math.toRadians(azimuthDegrees);
        double elevation = Math.toRadians(elevationDegrees);
        double bank = Math.toRadians(bankDegrees);
        double[] forward = {
                Math.sin(az) * Math.cos(elevation),
                Math.cos(az) * Math.cos(elevation),
                Math.sin(elevation)
        };
        double[] levelRight = {Math.cos(az), -Math.sin(az), 0.0};
        double[] levelUp = unit(cross(levelRight, forward));
        double c = Math.cos(bank), s = Math.sin(bank);
        double[] right = add(scale(levelRight, c), scale(levelUp, s));
        return new Basis(right, levelUp, forward);
    }

    public static Frame manualFrame(double azimuthDegrees, double elevationDegrees, double rollDegrees) {
        Basis b = manualBasis(azimuthDegrees, elevationDegrees, rollDegrees);
        return new Frame(b.right, b.up, b.forward);
    }

    /** Perspective-projects an ENU direction. Vertical FOV is used; points behind are clipped. */
    public static Point project(double[] enuDirection, Basis camera, double verticalFovDegrees,
                                double width, double height) {
        requireViewport(verticalFovDegrees, width, height);
        double depth = dot(enuDirection, camera.forward);
        if (!(depth > EPSILON)) return new Point(Double.NaN, Double.NaN, depth, false);
        double focal = height * 0.5 / Math.tan(Math.toRadians(verticalFovDegrees) * 0.5);
        double x = width * 0.5 + focal * dot(enuDirection, camera.right) / depth;
        double y = height * 0.5 - focal * dot(enuDirection, camera.up) / depth;
        boolean visible = x >= 0.0 && x <= width && y >= 0.0 && y <= height;
        return new Point(x, y, depth, visible);
    }

    public static Point project(double[] enuDirection, double[] right, double[] up,
                                double[] forward, double verticalFovDegrees,
                                double width, double height) {
        return project(enuDirection, new Basis(right, up, forward), verticalFovDegrees, width, height);
    }

    /** Returns the ENU ray passing through a screen pixel. */
    public static double[] rayForPixel(double x, double y, Basis camera,
                                       double verticalFovDegrees, double width, double height) {
        requireViewport(verticalFovDegrees, width, height);
        double focal = height * 0.5 / Math.tan(Math.toRadians(verticalFovDegrees) * 0.5);
        return unit(add(camera.forward,
                add(scale(camera.right, (x - width * 0.5) / focal),
                        scale(camera.up, (height * 0.5 - y) / focal))));
    }

    /** Converts magnetic-ENU coordinates to true-ENU; easterly declination is positive. */
    public static double[] magneticToTrue(double[] magneticEnu, double declinationDegrees) {
        checkVector(magneticEnu);
        double d = Math.toRadians(declinationDegrees), c = Math.cos(d), s = Math.sin(d);
        return new double[] {
                c * magneticEnu[0] + s * magneticEnu[1],
                -s * magneticEnu[0] + c * magneticEnu[1],
                magneticEnu[2]
        };
    }

    public static double[] enuFromAzimuthAltitude(double azimuthDegrees, double altitudeDegrees) {
        double az = Math.toRadians(azimuthDegrees), alt = Math.toRadians(altitudeDegrees);
        return new double[] {Math.sin(az) * Math.cos(alt), Math.cos(az) * Math.cos(alt), Math.sin(alt)};
    }

    /** Horizontal-coordinate convenience with the conventional altitude, azimuth argument order. */
    public static double[] horizontalVector(double altitudeDegrees, double azimuthDegrees) {
        return enuFromAzimuthAltitude(azimuthDegrees, altitudeDegrees);
    }

    private static void requireViewport(double fov, double width, double height) {
        if (!(fov > 0.0 && fov < 180.0) || !(width > 0.0) || !(height > 0.0))
            throw new IllegalArgumentException("FOV must be (0,180), and viewport dimensions positive");
    }
    private static double dot(double[] a, double[] b) { checkVector(a); checkVector(b); return a[0]*b[0]+a[1]*b[1]+a[2]*b[2]; }
    private static double[] cross(double[] a, double[] b) { return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]}; }
    private static double[] add(double[] a, double[] b) { return new double[]{a[0]+b[0],a[1]+b[1],a[2]+b[2]}; }
    private static double[] subtract(double[] a, double[] b) { return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]}; }
    private static double[] scale(double[] v, double s) { return new double[]{v[0]*s,v[1]*s,v[2]*s}; }
    private static double[] unit(double[] v) { checkVector(v); double n=Math.sqrt(dot(v,v)); if(n<EPSILON)throw new IllegalArgumentException("Zero-length vector"); return scale(v,1.0/n); }
    private static void checkVector(double[] v) { if(v==null||v.length<3)throw new IllegalArgumentException("Expected a 3-vector");for(int i=0;i<3;i++)if(!Double.isFinite(v[i]))throw new IllegalArgumentException("Vector must be finite"); }
}
