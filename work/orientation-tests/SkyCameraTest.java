import com.startrail.app.astro.SkyCamera;

public final class SkyCameraTest {
    private static void near(double expected,double actual){if(Math.abs(expected-actual)>1e-8)throw new AssertionError(expected+" != "+actual);}
    private static void yes(boolean v){if(!v)throw new AssertionError();}
    public static void main(String[] args){
        SkyCamera.Frame north=SkyCamera.manualFrame(0,0,0);
        near(0,north.forward[0]); near(1,north.forward[1]); near(0,north.forward[2]);
        SkyCamera.Point centre=SkyCamera.project(SkyCamera.horizontalVector(0,0),north,60,1000,500);
        near(500,centre.x);near(250,centre.y);yes(centre.inFront&&centre.onScreen);
        SkyCamera.Point east=SkyCamera.project(SkyCamera.horizontalVector(0,30),north,90,1000,500);
        yes(east.x>500); // an object east of a north-facing phone is on the right
        yes(!SkyCamera.project(SkyCamera.horizontalVector(0,180),north,90,1000,500).inFront);
        SkyCamera.Frame wrapA=SkyCamera.manualFrame(359.999,0,0),wrapB=SkyCamera.manualFrame(-0.001,0,0);
        near(wrapA.forward[0],wrapB.forward[0]);near(wrapA.forward[1],wrapB.forward[1]);
        SkyCamera.Frame zenith=SkyCamera.manualFrame(42,90,0);
        near(1,zenith.forward[2]);
        SkyCamera.Frame rolled=SkyCamera.manualFrame(0,0,90);
        near(1,rolled.right[2]);
        double[] trueNorth=SkyCamera.magneticToTrue(new double[]{0,1,0},10);
        yes(trueNorth[0]>0); // positive/east declination moves magnetic north east in true ENU
        double[] ray=SkyCamera.rayForPixel(500,250,north,60,1000,500);
        near(north.forward[0],ray[0]);near(north.forward[1],ray[1]);near(north.forward[2],ray[2]);
        System.out.println("SkyCamera tests passed");
    }
}
