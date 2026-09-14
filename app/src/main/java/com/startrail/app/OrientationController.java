package com.startrail.app;

import android.content.Context;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import com.startrail.app.astro.SkyCamera;

/** Lifecycle-aware, north-referenced phone orientation for a rear-facing sky camera. */
public final class OrientationController implements SensorEventListener {
    public interface Listener {
        void onOrientation(double[] right, double[] up, double[] forward, String status);
    }

    private static final long MIN_CALLBACK_NS = 20_000_000L; // at most 50 redraws/second
    private static final long STALE_DATA_MS = 3_000L;
    private static final float MIN_PLAUSIBLE_FIELD_UT = 20f;
    private static final float MAX_PLAUSIBLE_FIELD_UT = 80f;
    private final SensorManager sensors;
    private final Display display;
    private final Listener listener;
    private final Sensor magnetometer;
    private final Sensor rotationVector;
    private final Sensor geomagneticRotationVector;
    private final Sensor accelerometer;
    private final float[] gravity = new float[3];
    private final float[] magnetic = new float[3];
    private boolean haveGravity, haveMagnetic, running;
    private int magneticAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;
    private int orientationAccuracy = -1;
    private float magneticFieldStrengthUt = Float.NaN;
    private float declinationDegrees;
    private volatile double headingCorrectionDegrees;
    private long lastCallbackNs, lastMagneticNs;
    private boolean staleReported;
    private double[] smoothRight, smoothUp, smoothForward;
    private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable startupTimeout=this::onStartupTimeout;
    private final Runnable staleDataTimeout=this::onStaleDataTimeout;
    private void onStartupTimeout(){
        if(running&&lastCallbackNs==0){stop();listener.onOrientation(null,null,null,"未收到方向数据 · 请使用手动模式");}
    }
    private void onStaleDataTimeout() {
        if (running && lastCallbackNs != 0 && !staleReported) {
            staleReported = true;
            stop();
            listener.onOrientation(null, null, null, "方向数据已中断 · 请使用手动模式");
        }
    }

    public OrientationController(Context context, Listener listener) {
        Context app = context.getApplicationContext();
        this.sensors = (SensorManager) app.getSystemService(Context.SENSOR_SERVICE);
        WindowManager windows = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.display = windows==null?null:windows.getDefaultDisplay();
        this.listener = listener;
        magnetometer = sensors==null?null:sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        rotationVector = sensors==null?null:sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        geomagneticRotationVector = sensors==null?null:sensors.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
        accelerometer = sensors==null?null:sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    /** Updates the magnetic-to-true-north correction for the observing place and time. */
    public void setLocation(double latitude, double longitude, double altitudeMeters, long timeMillis) {
        declinationDegrees = new GeomagneticField((float) latitude, (float) longitude,
                (float) altitudeMeters, timeMillis).getDeclination();
    }

    public void setLocation(double latitude, double longitude, long timeMillis) {
        setLocation(latitude, longitude, 0.0, timeMillis);
    }

    /** Uses the present date because declination belongs to the physical sensor, not sky simulation time. */
    public void setLocation(double latitude, double longitude) {
        setLocation(latitude, longitude, 0.0, System.currentTimeMillis());
    }

    /** Sets a user calibration offset in degrees, normalized to [-180, 180). */
    public void setHeadingCorrection(double degrees) {
        if (!Double.isFinite(degrees)) throw new IllegalArgumentException("Heading correction must be finite");
        headingCorrectionDegrees = normalizeDegrees(degrees);
    }

    public double getHeadingCorrection() { return headingCorrectionDegrees; }

    /** Sensor-health gate, not an absolute heading-accuracy guarantee. */
    public boolean isDirectionReliable() {
        long now=SystemClock.elapsedRealtimeNanos();
        return running && lastCallbackNs!=0 && now-lastCallbackNs<3_000_000_000L
                && lastMagneticNs!=0 && now-lastMagneticNs<3_000_000_000L
                && magneticAccuracy>=SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
                && (orientationAccuracy<0||orientationAccuracy>=SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM)
                && Float.isFinite(magneticFieldStrengthUt)
                && magneticFieldStrengthUt>=MIN_PLAUSIBLE_FIELD_UT && magneticFieldStrengthUt<=MAX_PLAUSIBLE_FIELD_UT;
    }

    public boolean isSupported() {
        return magnetometer != null && (rotationVector != null || geomagneticRotationVector != null
                || accelerometer != null);
    }

    public boolean isAvailable() { return isSupported(); }

    public void start() {
        if (running) return;
        if (!isSupported()) {
            listener.onOrientation(null, null, null, "方向传感器不可用 · 请使用手动模式");
            return;
        }
        running = true;
        lastCallbackNs=0;lastMagneticNs=0;magneticAccuracy=SensorManager.SENSOR_STATUS_UNRELIABLE;orientationAccuracy=-1;magneticFieldStrengthUt=Float.NaN;
        staleReported = false;
        // Rotation vector is gyro-fused and north referenced. Game rotation vector is deliberately excluded.
        Sensor primary = rotationVector != null ? rotationVector : geomagneticRotationVector;
        boolean registered = primary != null
                ? sensors.registerListener(this, primary, SensorManager.SENSOR_DELAY_GAME)
                : sensors.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        registered &= sensors.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME);
        if (!registered) {
            sensors.unregisterListener(this);
            running = false;
            listener.onOrientation(null, null, null, "方向传感器启动失败 · 请使用手动模式");
            return;
        }
        listener.onOrientation(null, null, null, "正在校准方向…");
        handler.postDelayed(startupTimeout,6000);
    }

    public void stop() {
        handler.removeCallbacks(startupTimeout);
        handler.removeCallbacks(staleDataTimeout);
        if (!running) return;
        sensors.unregisterListener(this);
        running = false;
        haveGravity = haveMagnetic = false;
        smoothRight = smoothUp = smoothForward = null;
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (!running || event == null || event.sensor == null || event.values == null) return;
        for(float value:event.values)if(!Float.isFinite(value))return;
        int type = event.sensor.getType();
        if (type == Sensor.TYPE_MAGNETIC_FIELD) {
            if (event.values.length < 3) return;
            System.arraycopy(event.values, 0, magnetic, 0, 3);
            haveMagnetic = true;lastMagneticNs=SystemClock.elapsedRealtimeNanos();
            magneticFieldStrengthUt = (float) Math.sqrt(magnetic[0] * magnetic[0]
                    + magnetic[1] * magnetic[1] + magnetic[2] * magnetic[2]);
            magneticAccuracy = event.accuracy;
            if (rotationVector != null || geomagneticRotationVector != null) return;
        }

        float[] matrix = new float[9];
        if (type == Sensor.TYPE_ROTATION_VECTOR || type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) {
            if (event.values.length < 3) return;
            orientationAccuracy = event.accuracy;
            SensorManager.getRotationMatrixFromVector(matrix, event.values);
        } else if (type == Sensor.TYPE_ACCELEROMETER) {
            if (event.values.length < 3) return;
            System.arraycopy(event.values, 0, gravity, 0, 3);
            haveGravity = true;
            if (!haveMagnetic || !SensorManager.getRotationMatrix(matrix, null, gravity, magnetic)) return;
        } else return;
        publish(matrix, event.timestamp);
    }

    private void publish(float[] r, long sensorTimestampNs) {
        long now = SystemClock.elapsedRealtimeNanos();
        if (lastCallbackNs != 0 && now - lastCallbackNs < MIN_CALLBACK_NS) return;
        SkyCamera.Frame frame = cameraFrame(r, display==null?Surface.ROTATION_0:display.getRotation(),
                declinationDegrees, headingCorrectionDegrees);
        double[] right = frame.right, up = frame.up, forward = frame.forward;

        double alpha = smoothForward == null || dot(smoothForward,forward)<-0.8 || dot(smoothRight,right)<-0.8
                ? 1.0 : Math.min(1,1-Math.exp(-(now-lastCallbackNs)/90_000_000.0));
        smoothForward = normalized(blend(smoothForward, forward, alpha));
        double[] candidateRight = normalized(blend(smoothRight, right, alpha));
        candidateRight = normalized(subtract(candidateRight,
                scale(smoothForward, dot(candidateRight, smoothForward))));
        smoothRight = candidateRight;
        smoothUp = normalized(cross(smoothRight, smoothForward));
        lastCallbackNs = now;
        staleReported = false;
        listener.onOrientation(smoothRight.clone(), smoothUp.clone(), smoothForward.clone(), status());
        handler.removeCallbacks(startupTimeout);
        handler.removeCallbacks(staleDataTimeout);
        handler.postDelayed(staleDataTimeout, STALE_DATA_MS);
    }

    /** Converts an Android device-to-magnetic-ENU matrix into the displayed rear-camera frame. */
    public static SkyCamera.Frame cameraFrame(float[] r, int displayRotation, float declinationDegrees) {
        return cameraFrame(r, displayRotation, declinationDegrees, 0.0);
    }

    /** As above, then applies the user's horizontal heading offset in true-north coordinates. */
    public static SkyCamera.Frame cameraFrame(float[] r, int displayRotation, float declinationDegrees,
                                               double headingCorrectionDegrees) {
        if (r == null || r.length < 9) throw new IllegalArgumentException("Expected a 3x3 matrix");
        for (int i = 0; i < 9; i++) {
            if (!Float.isFinite(r[i])) throw new IllegalArgumentException("Matrix values must be finite");
        }
        if (!Double.isFinite(headingCorrectionDegrees))
            throw new IllegalArgumentException("Heading correction must be finite");
        double[] deviceX = {r[0], r[3], r[6]};
        double[] deviceY = {r[1], r[4], r[7]};
        double[] deviceZ = {r[2], r[5], r[8]};
        double[] right, up;
        switch (displayRotation) {
            case Surface.ROTATION_90: right=deviceY; up=negate(deviceX); break;
            case Surface.ROTATION_180: right=negate(deviceX); up=negate(deviceY); break;
            case Surface.ROTATION_270: right=negate(deviceY); up=deviceX; break;
            default: right=deviceX; up=deviceY;
        }
        double[] forward = negate(deviceZ); // view through the back of the phone
        right = SkyCamera.magneticToTrue(right, declinationDegrees);
        up = SkyCamera.magneticToTrue(up, declinationDegrees);
        forward = SkyCamera.magneticToTrue(forward, declinationDegrees);
        right = rotateAboutUpAxis(right, headingCorrectionDegrees);
        up = rotateAboutUpAxis(up, headingCorrectionDegrees);
        forward = rotateAboutUpAxis(forward, headingCorrectionDegrees);
        return new SkyCamera.Frame(right, up, forward);
    }

    private String status() {
        if(lastMagneticNs==0||SystemClock.elapsedRealtimeNanos()-lastMagneticNs>3_000_000_000L)return "磁场数据不足 · 方位仅供参考";
        int accuracy = orientationAccuracy < 0 ? magneticAccuracy
                : Math.min(magneticAccuracy, orientationAccuracy);
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) return "磁场受干扰 · 移开磁性物体并画 8 字校准";
        if (Float.isFinite(magneticFieldStrengthUt)
                && (magneticFieldStrengthUt < MIN_PLAUSIBLE_FIELD_UT || magneticFieldStrengthUt > MAX_PLAUSIBLE_FIELD_UT))
            return "附近磁场异常 · 移开磁性物体后重试";
        if (accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW) return "方向精度较低 · 请画 8 字校准";
        if (accuracy == SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) return "方向可用 · 传感器精度中等";
        return "方向可用 · 已按真北修正";
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (sensor == null) return;
        if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) magneticAccuracy = accuracy;
        else if (sensor.getType() == Sensor.TYPE_ROTATION_VECTOR
                || sensor.getType() == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) orientationAccuracy = accuracy;
    }

    private static double[] negate(double[] v){return new double[]{-v[0],-v[1],-v[2]};}
    private static double normalizeDegrees(double degrees) {
        double normalized = ((degrees + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
        return normalized == -0.0 ? 0.0 : normalized;
    }
    private static double[] rotateAboutUpAxis(double[] v, double degrees) {
        double radians = Math.toRadians(normalizeDegrees(degrees));
        double sin = Math.sin(radians), cos = Math.cos(radians);
        return new double[]{v[0] * cos + v[1] * sin, v[1] * cos - v[0] * sin, v[2]};
    }
    private static double[] blend(double[] a,double[] b,double t){if(a==null)return b.clone();return new double[]{a[0]+t*(b[0]-a[0]),a[1]+t*(b[1]-a[1]),a[2]+t*(b[2]-a[2])};}
    private static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private static double[] subtract(double[] a,double[] b){return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    private static double[] scale(double[] v,double s){return new double[]{v[0]*s,v[1]*s,v[2]*s};}
    private static double[] cross(double[] a,double[] b){return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    private static double[] normalized(double[] v){double n=Math.sqrt(dot(v,v));return n<1e-9?new double[]{1,0,0}:scale(v,1/n);}
}
