package com.startrail.app;
import android.test.InstrumentationTestCase;
import android.os.SystemClock;
import java.lang.reflect.Field;

public final class OrientationReliabilityTest extends InstrumentationTestCase {
    private void set(OrientationController c,String name,Object value)throws Exception{Field f=OrientationController.class.getDeclaredField(name);f.setAccessible(true);f.set(c,value);}
    private OrientationController good()throws Exception{
        OrientationController c=new OrientationController(getInstrumentation().getTargetContext(),(r,u,f,s)->{});
        set(c,"running",true);set(c,"lastCallbackNs",SystemClock.elapsedRealtimeNanos());set(c,"lastMagneticNs",SystemClock.elapsedRealtimeNanos());set(c,"magneticAccuracy",3);set(c,"orientationAccuracy",3);set(c,"magneticFieldStrengthUt",45f);return c;
    }
    public void testFreshHealthySignalsPermitGuidance()throws Exception{OrientationController c=good();assertTrue(c.isDirectionReliable());}
    public void testStaleOrMissingStreamCannotConfirmAlignment()throws Exception{OrientationController c=good();set(c,"lastCallbackNs",SystemClock.elapsedRealtimeNanos()-4_000_000_000L);assertFalse(c.isDirectionReliable());c=good();set(c,"lastMagneticNs",0L);assertFalse(c.isDirectionReliable());}
    public void testInterferenceAndLowAccuracyCannotConfirmAlignment()throws Exception{OrientationController c=good();set(c,"magneticFieldStrengthUt",180f);assertFalse(c.isDirectionReliable());c=good();set(c,"orientationAccuracy",1);assertFalse(c.isDirectionReliable());c=good();set(c,"running",false);assertFalse(c.isDirectionReliable());}
}
