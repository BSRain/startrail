package com.startrail.app.astro;

/** Pure geometry for guiding a camera toward an ENU sky direction. */
public final class Guidance {
    private Guidance() {}

    public static final class Result {
        public final double angularSeparation;
        public final double screenX;
        public final double screenY;
        public final boolean belowHorizon;
        public final boolean aligned;

        private Result(double angularSeparation, double screenX, double screenY,
                       boolean belowHorizon, boolean aligned) {
            this.angularSeparation = angularSeparation;
            this.screenX = screenX;
            this.screenY = screenY;
            this.belowHorizon = belowHorizon;
            this.aligned = aligned;
        }
    }

    /**
     * Returns target direction in camera screen coordinates. screenY is positive downward.
     * Alignment is deliberately impossible below the astronomical horizon.
     */
    public static Result calculate(double[] target, SkyCamera.Basis camera,
                                   double alignedThresholdDegrees) {
        if (camera == null || !Double.isFinite(alignedThresholdDegrees) || alignedThresholdDegrees < 0)
            throw new IllegalArgumentException("Camera and a finite non-negative threshold are required");
        double[] direction = unit(target);
        double forward = clamp(dot(direction, camera.forward));
        double separation = Math.toDegrees(Math.acos(forward));
        double x = dot(direction, camera.right);
        double y = -dot(direction, camera.up);
        double length = Math.hypot(x, y);
        if (length < 1e-9) {
            // At the antipode every turn is equivalent; prefer an upward cue.
            x = 0;
            y = -1;
        } else {
            x /= length;
            y /= length;
        }
        boolean below = direction[2] < 0;
        return new Result(separation, x, y, below,
                !below && separation <= alignedThresholdDegrees);
    }

    /** Chinese camera-relative movement cue. Manual dragging moves the camera opposite the target. */
    public static String directionCue(double screenX, double screenY, boolean manualMode) {
        if (!Double.isFinite(screenX) || !Double.isFinite(screenY))
            throw new IllegalArgumentException("Screen direction must be finite");
        String horizontal=Math.abs(screenX)<0.25?"":(screenX>0?"右":"左");
        String vertical=Math.abs(screenY)<0.25?"":(screenY>0?"下":"上");
        String direction=horizontal+vertical;
        if(direction.isEmpty())direction="中央";
        if(!manualMode)return "向"+direction+"转动手机";
        String dragHorizontal=Math.abs(screenX)<0.25?"":(screenX>0?"左":"右");
        String dragVertical=Math.abs(screenY)<0.25?"":(screenY>0?"上":"下");
        String dragDirection=dragHorizontal+dragVertical;
        return dragDirection.isEmpty()?"保持目标在中央":"向"+dragDirection+"拖动星图";
    }

    private static double dot(double[] a, double[] b) {
        if (a == null || b == null || a.length < 3 || b.length < 3)
            throw new IllegalArgumentException("Expected 3-vectors");
        return a[0]*b[0] + a[1]*b[1] + a[2]*b[2];
    }

    private static double[] unit(double[] value) {
        if (value == null || value.length < 3) throw new IllegalArgumentException("Expected a 3-vector");
        double sum = 0;
        for (int i=0;i<3;i++) {
            if (!Double.isFinite(value[i])) throw new IllegalArgumentException("Direction must be finite");
            sum += value[i]*value[i];
        }
        double length=Math.sqrt(sum);
        if (!(length>1e-12)) throw new IllegalArgumentException("Direction must be non-zero");
        return new double[]{value[0]/length,value[1]/length,value[2]/length};
    }

    private static double clamp(double value) { return Math.max(-1, Math.min(1, value)); }
}
